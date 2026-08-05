import PhotosUI
import SwiftUI
import UIKit

enum GallerySelectionKey: Hashable {
    case photo(planID: UUID, photoID: UUID)
    case cover(planID: UUID)
}

enum GallerySelectionPolicy {
    static func selectAll(_ keys: [GallerySelectionKey]) -> Set<GallerySelectionKey> { Set(keys) }

    static func retain(
        _ selection: Set<GallerySelectionKey>,
        available: Set<GallerySelectionKey>
    ) -> Set<GallerySelectionKey> {
        selection.intersection(available)
    }

    static func summary(_ keys: Set<GallerySelectionKey>) -> String {
        let photos = keys.filter {
            if case .photo = $0 { return true }
            return false
        }.count
        let covers = keys.count - photos
        return switch (photos, covers) {
        case (0, let covers): "删除 \(covers) 张自定义预览图？"
        case (let photos, 0): "删除 \(photos) 张照片？"
        default: "删除 \(photos) 张照片和 \(covers) 张自定义预览图？"
        }
    }

    static func deleteRequest(_ keys: Set<GallerySelectionKey>) -> GalleryDeleteRequest {
        var photos: [UUID: Set<UUID>] = [:]
        var covers: Set<UUID> = []
        for key in keys {
            switch key {
            case let .photo(planID, photoID): photos[planID, default: []].insert(photoID)
            case let .cover(planID): covers.insert(planID)
            }
        }
        return GalleryDeleteRequest(photoIDsByPlan: photos, coverPlanIDs: covers)
    }
}

struct PlanGalleryView: View {
    let planID: UUID
    @ObservedObject var store: PlanStore
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var isManaging = false
    @State private var selection: Set<GallerySelectionKey> = []
    @State private var confirmsDeletion = false
    @State private var message: String?

    private var plan: TravelPlan? { store.plan(id: planID) }
    private var keys: [GallerySelectionKey] {
        guard let plan else { return [] }
        return (plan.coverRelativePath == nil ? [] : [.cover(planID: planID)])
            + plan.photos.map { .photo(planID: planID, photoID: $0.id) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
                    Label("添加照片", systemImage: "photo.badge.plus")
                }
                .buttonStyle(.borderedProminent)
                Spacer()
                Button(isManaging ? "完成" : "管理") {
                    isManaging.toggle()
                    if !isManaging { selection.removeAll() }
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("管理相册")
            }
            .padding()

            if keys.isEmpty {
                ContentUnavailableView(
                    "还没有照片",
                    systemImage: "photo.on.rectangle",
                    description: Text("从系统照片中选择后，旅笺会复制一份到应用私有目录")
                )
                .accessibilityIdentifier("空相册提示")
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 10)], spacing: 10) {
                        if let path = plan?.coverRelativePath {
                            galleryTile(path: path, key: .cover(planID: planID), label: "自定义预览图")
                        }
                        ForEach(plan?.photos ?? []) { photo in
                            galleryTile(
                                path: photo.relativePath,
                                key: .photo(planID: planID, photoID: photo.id),
                                label: photo.placeID ?? "旅行照片"
                            )
                        }
                    }
                    .padding()
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if isManaging && !keys.isEmpty { managementBar }
        }
        .onChange(of: pickerItems) { importPhotos(pickerItems) }
        .onChange(of: keys) {
            selection = GallerySelectionPolicy.retain(selection, available: Set(keys))
        }
        .confirmationDialog(
            GallerySelectionPolicy.summary(selection),
            isPresented: $confirmsDeletion,
            titleVisibility: .visible
        ) {
            Button("删除私有副本", role: .destructive) { deleteSelection() }
            Button("取消", role: .cancel) {}
        } message: { Text("不会删除系统相册中的原图。") }
        .alert("相册", isPresented: messageBinding) {
            Button("知道了", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private func galleryTile(path: String, key: GallerySelectionKey, label: String) -> some View {
        Button {
            guard isManaging else { return }
            if selection.contains(key) { selection.remove(key) } else { selection.insert(key) }
        } label: {
            ZStack(alignment: .topTrailing) {
                PrivateImage(store: store, relativePath: path)
                    .frame(minHeight: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                if isManaging {
                    Image(systemName: selection.contains(key) ? "checkmark.circle.fill" : "circle")
                        .font(.title2)
                        .foregroundStyle(selection.contains(key) ? LujianPalette.coral : .white)
                        .shadow(radius: 2)
                        .padding(8)
                }
                VStack {
                    Spacer()
                    Text(label)
                        .font(.caption.bold())
                        .foregroundStyle(.white)
                        .padding(6)
                        .frame(maxWidth: .infinity)
                        .background(.black.opacity(0.45))
                }
            }
            .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
    }

    private var managementBar: some View {
        HStack {
            Button(selection.count == keys.count ? "取消全选" : "全选") {
                selection = selection.count == keys.count ? [] : GallerySelectionPolicy.selectAll(keys)
            }
            .buttonStyle(.bordered)
            Spacer()
            Button("删除 \(selection.count) 项", role: .destructive) { confirmsDeletion = true }
                .buttonStyle(.borderedProminent)
                .disabled(selection.isEmpty)
        }
        .padding()
        .background(.bar)
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { message != nil }, set: { if !$0 { message = nil } })
    }

    private func importPhotos(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task { @MainActor in
            var imported = 0
            for item in items {
                guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                if (try? store.savePhoto(data: data, planID: planID, placeID: nil)) != nil { imported += 1 }
            }
            pickerItems = []
            message = imported == items.count ? "已添加 \(imported) 张照片" : "已添加 \(imported) 张，部分图片格式不受支持"
        }
    }

    private func deleteSelection() {
        do {
            try store.deleteGalleryItems(GallerySelectionPolicy.deleteRequest(selection))
            selection.removeAll()
        } catch {
            message = error.localizedDescription
        }
    }
}

struct GlobalGalleryView: View {
    @ObservedObject var store: PlanStore
    @State private var isManaging = false
    @State private var selection: Set<GallerySelectionKey> = []
    @State private var confirmsDeletion = false
    @State private var message: String?

    private var keys: [GallerySelectionKey] {
        store.plans.flatMap { plan in
            (plan.coverRelativePath == nil ? [] : [.cover(planID: plan.id)])
                + plan.photos.map { .photo(planID: plan.id, photoID: $0.id) }
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if keys.isEmpty {
                    ContentUnavailableView(
                        "相册还是空的",
                        systemImage: "photo.stack",
                        description: Text("进入任一计划的相册添加照片")
                    )
                    .accessibilityIdentifier("空相册提示")
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 22) {
                            ForEach(store.plans.filter { $0.coverRelativePath != nil || !$0.photos.isEmpty }) { plan in
                                VStack(alignment: .leading, spacing: 10) {
                                    Text(plan.title).font(.title3.bold())
                                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 10)], spacing: 10) {
                                        if let path = plan.coverRelativePath {
                                            tile(path: path, key: .cover(planID: plan.id))
                                        }
                                        ForEach(plan.photos) { photo in
                                            tile(path: photo.relativePath, key: .photo(planID: plan.id, photoID: photo.id))
                                        }
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
            .background(LujianPalette.paperDeep.opacity(0.35))
            .navigationTitle("相册")
            .toolbar {
                Button(isManaging ? "完成" : "管理") {
                    isManaging.toggle()
                    if !isManaging { selection.removeAll() }
                }
                .accessibilityIdentifier("管理相册")
            }
            .safeAreaInset(edge: .bottom) {
                if isManaging && !keys.isEmpty {
                    HStack {
                        Button(selection.count == keys.count ? "取消全选" : "全选") {
                            selection = selection.count == keys.count ? [] : GallerySelectionPolicy.selectAll(keys)
                        }
                        Spacer()
                        Button("删除 \(selection.count) 项", role: .destructive) { confirmsDeletion = true }
                            .disabled(selection.isEmpty)
                    }
                    .buttonStyle(.bordered)
                    .padding()
                    .background(.bar)
                }
            }
            .confirmationDialog(
                GallerySelectionPolicy.summary(selection),
                isPresented: $confirmsDeletion,
                titleVisibility: .visible
            ) {
                Button("删除私有副本", role: .destructive) { deleteSelection() }
                Button("取消", role: .cancel) {}
            } message: { Text("不会删除系统相册中的原图。") }
            .alert("相册", isPresented: messageBinding) {
                Button("知道了", role: .cancel) { message = nil }
            } message: { Text(message ?? "") }
        }
    }

    private func tile(path: String, key: GallerySelectionKey) -> some View {
        Button {
            guard isManaging else { return }
            if selection.contains(key) { selection.remove(key) } else { selection.insert(key) }
        } label: {
            ZStack(alignment: .topTrailing) {
                PrivateImage(store: store, relativePath: path)
                    .aspectRatio(1, contentMode: .fill)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                if isManaging {
                    Image(systemName: selection.contains(key) ? "checkmark.circle.fill" : "circle")
                        .font(.title2)
                        .foregroundStyle(selection.contains(key) ? LujianPalette.coral : .white)
                        .shadow(radius: 2)
                        .padding(8)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { message != nil }, set: { if !$0 { message = nil } })
    }

    private func deleteSelection() {
        do {
            try store.deleteGalleryItems(GallerySelectionPolicy.deleteRequest(selection))
            selection.removeAll()
        } catch {
            message = error.localizedDescription
        }
    }
}

private struct PrivateImage: View {
    let store: PlanStore
    let relativePath: String

    var body: some View {
        if let url = try? store.privateFileURL(relativePath: relativePath),
           let data = try? Data(contentsOf: url),
           let image = UIImage(data: data) {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            ZStack {
                LujianPalette.paperDeep
                Image(systemName: "photo").font(.largeTitle).foregroundStyle(.secondary)
            }
        }
    }
}
