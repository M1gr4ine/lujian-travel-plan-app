import MapKit
import SwiftUI
import UniformTypeIdentifiers

enum PlanDetailSection: String, CaseIterable, Hashable {
    case itinerary
    case map
    case budget
    case gallery

    var title: String {
        switch self {
        case .itinerary: "行程"
        case .map: "地图"
        case .budget: "预算"
        case .gallery: "相册"
        }
    }
}

enum DailyRoutePolicy {
    static func stops(for day: PlanDay) -> [PlanMapStop] {
        day.mapStops.filter { stop in
            guard let latitude = stop.latitude, let longitude = stop.longitude else { return false }
            return (-90.0...90.0).contains(latitude) && (-180.0...180.0).contains(longitude)
        }
    }

    static func stops(for day: PlanDay, places: [PlanPlace]) -> [PlanMapStop] {
        let explicit = stops(for: day)
        if !explicit.isEmpty { return explicit }
        let placesByID = Dictionary(uniqueKeysWithValues: places.map { ($0.id, $0) })
        return day.items.compactMap { item in
            guard let placeID = item.placeID,
                  let place = placesByID[placeID],
                  let latitude = place.latitude,
                  let longitude = place.longitude else { return nil }
            return PlanMapStop(
                id: item.id,
                title: item.title,
                time: item.time,
                category: item.category,
                latitude: latitude,
                longitude: longitude
            )
        }
    }
}

enum DetailSelectionPolicy {
    static func validDayID(_ selected: String?, in days: [PlanDay]) -> String? {
        days.contains(where: { $0.id == selected }) ? selected : days.first?.id
    }
}

struct PlanDetailView: View {
    let planID: UUID
    @ObservedObject var store: PlanStore
    @State private var section: PlanDetailSection = .itinerary
    @State private var selectedDayID: String?
    @State private var showsEditor = false
    @State private var webItem: WebFileItem?
    @State private var exportDocument = HTMLPlanDocument()
    @State private var showsExporter = false
    @State private var message: String?

    private var plan: TravelPlan? { store.plan(id: planID) }

    var body: some View {
        Group {
            if let plan {
                VStack(spacing: 0) {
                    Picker("计划详情分区", selection: $section) {
                        ForEach(PlanDetailSection.allCases, id: \.self) { value in
                            Text(value.title).tag(value)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding()
                    .accessibilityIdentifier("计划详情分区")

                    Group {
                        switch section {
                        case .itinerary:
                            itinerary(plan)
                        case .map:
                            dailyMap(plan)
                        case .budget:
                            budget(plan)
                        case .gallery:
                            PlanGalleryView(planID: plan.id, store: store)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .background(LujianPalette.paperDeep.opacity(0.35))
                .navigationTitle(plan.title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { detailToolbar(plan) }
                .onAppear { selectedDayID = DetailSelectionPolicy.validDayID(selectedDayID, in: plan.days) }
                .onChange(of: plan.days.map(\.id)) {
                    selectedDayID = DetailSelectionPolicy.validDayID(selectedDayID, in: plan.days)
                }
                .sheet(isPresented: $showsEditor) {
                    NavigationStack {
                        PlanEditorView(plan: plan) { updated, coverData, removesCover in
                            try store.upsert(updated)
                            if let coverData {
                                try store.saveCover(data: coverData, planID: updated.id)
                            } else if removesCover {
                                try store.deleteGalleryItems(
                                    GalleryDeleteRequest(coverPlanIDs: [updated.id])
                                )
                            }
                        }
                    }
                }
                .sheet(item: $webItem) { item in
                    NavigationStack {
                        SecureWebView(fileURL: item.url)
                            .navigationTitle("原始 HTML")
                            .navigationBarTitleDisplayMode(.inline)
                    }
                }
                .fileExporter(
                    isPresented: $showsExporter,
                    document: exportDocument,
                    contentType: .html,
                    defaultFilename: "\(plan.title)-plan.html"
                ) { result in
                    if case let .failure(error) = result { message = error.localizedDescription }
                }
                .alert("操作结果", isPresented: messageBinding) {
                    Button("知道了", role: .cancel) { message = nil }
                } message: { Text(message ?? "") }
            } else {
                ContentUnavailableView("计划不存在", systemImage: "exclamationmark.triangle")
            }
        }
    }

    @ToolbarContentBuilder
    private func detailToolbar(_ plan: TravelPlan) -> some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button("编辑", systemImage: "pencil") { showsEditor = true }
            Menu("更多", systemImage: "ellipsis.circle") {
                Button("查看原始 HTML", systemImage: "doc.text.magnifyingglass") { openOriginal(plan) }
                    .disabled(plan.originalHTMLRelativePath == nil)
                Button("导出 HTML", systemImage: "square.and.arrow.up") { export(plan) }
            }
        }
    }

    private func itinerary(_ plan: TravelPlan) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                planHeader(plan)
                ForEach(plan.days) { day in
                    VStack(alignment: .leading, spacing: 12) {
                        Text("\(day.label) · \(day.title)")
                            .font(.title3.bold())
                        if let summary = day.summary { Text(summary).foregroundStyle(.secondary) }
                        ForEach(day.items) { item in
                            HStack(alignment: .top, spacing: 12) {
                                Text(item.time ?? "待定")
                                    .font(.subheadline.monospacedDigit().bold())
                                    .foregroundStyle(LujianPalette.coral)
                                    .frame(width: 54, alignment: .leading)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title).font(.headline)
                                    if let notes = item.notes, !notes.isEmpty { Text(notes).font(.subheadline) }
                                    HStack {
                                        if let category = item.category { Text(category) }
                                        if let cost = item.cost { Text(cost) }
                                    }
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .paperCard()
                }
            }
            .padding()
        }
    }

    private func planHeader(_ plan: TravelPlan) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(plan.destinations.map(\.name).joined(separator: " · "))
                .font(.title.bold())
            if let dateRange = plan.dateRange { Label(dateRange, systemImage: "calendar") }
            if let travelers = plan.travelers { Label(travelers, systemImage: "person.2") }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .paperCard()
    }

    private func dailyMap(_ plan: TravelPlan) -> some View {
        VStack(spacing: 0) {
            if !plan.days.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(plan.days) { day in
                            Button(day.label) { selectedDayID = day.id }
                                .buttonStyle(.borderedProminent)
                                .tint(selectedDayID == day.id ? LujianPalette.coral : .gray)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 10)
                }
            }
            if let day = plan.days.first(where: { $0.id == selectedDayID }) ?? plan.days.first {
                DailyRouteMap(day: day, places: plan.places)
            } else {
                ContentUnavailableView("没有每日路线", systemImage: "map")
            }
        }
    }

    private func budget(_ plan: TravelPlan) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                BudgetRow(title: "总预算", value: plan.budget ?? "未填写", icon: "yensign.circle")
                BudgetRow(title: "住宿预算", value: plan.accommodationBudget ?? "未填写", icon: "bed.double")
                ForEach(plan.days) { day in
                    if let value = day.budget, !value.isEmpty {
                        BudgetRow(title: day.label, value: value, icon: "calendar")
                    }
                }
                ForEach(plan.sections) { section in
                    BudgetRow(title: section.title, value: section.content, icon: "note.text")
                }
            }
            .padding()
        }
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { message != nil }, set: { if !$0 { message = nil } })
    }

    private func openOriginal(_ plan: TravelPlan) {
        guard let path = plan.originalHTMLRelativePath else { return }
        do { webItem = WebFileItem(url: try store.privateFileURL(relativePath: path)) }
        catch { message = error.localizedDescription }
    }

    private func export(_ plan: TravelPlan) {
        do {
            exportDocument = HTMLPlanDocument(data: try PlanHTMLExporter.data(for: plan))
            showsExporter = true
        } catch {
            message = error.localizedDescription
        }
    }
}

private struct DailyRouteMap: View {
    let day: PlanDay
    let places: [PlanPlace]
    @State private var position: MapCameraPosition = .automatic

    private var stops: [PlanMapStop] { DailyRoutePolicy.stops(for: day, places: places) }
    private var coordinates: [CLLocationCoordinate2D] {
        stops.compactMap { stop in
            guard let latitude = stop.latitude, let longitude = stop.longitude else { return nil }
            return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
    }

    var body: some View {
        VStack(spacing: 10) {
            if coordinates.isEmpty {
                ContentUnavailableView("当天没有可用坐标", systemImage: "mappin.slash")
            } else {
                Map(position: $position) {
                    ForEach(stops) { stop in
                        if let latitude = stop.latitude, let longitude = stop.longitude {
                            Marker(
                                stop.title,
                                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
                            )
                            .tint(LujianPalette.coral)
                        }
                    }
                    if coordinates.count > 1 {
                        MapPolyline(coordinates: coordinates)
                            .stroke(LujianPalette.coral, lineWidth: 4)
                    }
                }
                .mapStyle(.standard(elevation: .flat))
                .accessibilityIdentifier("每日路线地图")
            }

            HStack {
                if let distance = day.distanceEstimate { Label(distance, systemImage: "point.topleft.down.to.point.bottomright.curvepath") }
                if let duration = day.durationEstimate { Label(duration, systemImage: "clock") }
            }
            .font(.subheadline)
            .padding(.horizontal)
        }
    }
}

private struct BudgetRow: View {
    let title: String
    let value: String
    let icon: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).foregroundStyle(LujianPalette.coral)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(value).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .paperCard()
    }
}

private struct WebFileItem: Identifiable {
    let id = UUID()
    let url: URL
}

private struct HTMLPlanDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.html] }
    var data = Data()

    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
