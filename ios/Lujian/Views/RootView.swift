import SwiftUI
import UniformTypeIdentifiers

enum RootTab: Hashable {
    case home
    case board
    case gallery
    case profile
}

struct RootView: View {
    @ObservedObject var store: PlanStore
    @State private var selectedTab: RootTab = .home
    @State private var showsImporter = false
    @State private var importMessage: String?

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeMapView(store: store)
                .tabItem { Label("首页", systemImage: "map") }
                .tag(RootTab.home)

            PlanBoardView(store: store, onImport: { showsImporter = true })
                .tabItem { Label("旅笺板", systemImage: "suitcase") }
                .tag(RootTab.board)

            GlobalGalleryView(store: store)
                .tabItem { Label("相册", systemImage: "photo.on.rectangle.angled") }
                .tag(RootTab.gallery)

            ProfileView(store: store)
                .tabItem { Label("我", systemImage: "person.crop.circle") }
                .tag(RootTab.profile)
        }
        .tint(LujianPalette.coral)
        .fileImporter(
            isPresented: $showsImporter,
            allowedContentTypes: [.html],
            allowsMultipleSelection: false
        ) { result in
            guard case let .success(urls) = result, let url = urls.first else {
                if case let .failure(error) = result { importMessage = error.localizedDescription }
                return
            }
            importURL(url)
        }
        .onOpenURL(perform: importURL)
        .alert("HTML 导入", isPresented: importAlertBinding) {
            Button("知道了", role: .cancel) { importMessage = nil }
        } message: {
            Text(importMessage ?? "")
        }
    }

    private var importAlertBinding: Binding<Bool> {
        Binding(
            get: { importMessage != nil },
            set: { if !$0 { importMessage = nil } }
        )
    }

    private func importURL(_ url: URL) {
        Task { @MainActor in
            do {
                let result = try await PlanImportService(store: store).importURL(url)
                let title = store.plan(id: result.planID)?.title ?? "旅行计划"
                importMessage = result.replacedExisting ? "已更新“\(title)”" : "已导入“\(title)”"
                selectedTab = .board
            } catch {
                importMessage = error.localizedDescription
            }
        }
    }
}

#Preview {
    RootView(store: .temporary(root: FileManager.default.temporaryDirectory.appendingPathComponent("LujianPreview")))
}
