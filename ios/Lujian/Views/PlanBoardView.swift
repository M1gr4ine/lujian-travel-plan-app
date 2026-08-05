import SwiftUI

enum BoardMode: String, CaseIterable, Hashable {
    case active
    case archived

    var title: String { self == .active ? "计划板" : "足迹板" }
}

enum BoardSelectionPolicy {
    static func selectAll(plans: [TravelPlan], mode: BoardMode) -> Set<UUID> {
        Set(plans.filter { $0.isArchived == (mode == .archived) }.map(\.id))
    }

    static func retain(_ selection: Set<UUID>, in visiblePlans: [TravelPlan]) -> Set<UUID> {
        selection.intersection(visiblePlans.map(\.id))
    }
}

struct PlanBoardView: View {
    @ObservedObject var store: PlanStore
    let onImport: () -> Void
    @State private var mode: BoardMode = .active
    @State private var isManaging = false
    @State private var selection: Set<UUID> = []
    @State private var confirmsDeletion = false
    @State private var operationMessage: String?

    private var visiblePlans: [TravelPlan] {
        store.plans
            .filter { $0.isArchived == (mode == .archived) }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    var body: some View {
        NavigationStack {
            Group {
                if visiblePlans.isEmpty {
                    ContentUnavailableView(
                        mode == .active ? "还没有旅行计划" : "足迹板还是空的",
                        systemImage: mode == .active ? "suitcase" : "figure.walk",
                        description: Text(mode == .active ? "导入一份 HTML 旅行计划开始吧" : "归档完成的计划会出现在这里")
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            ForEach(visiblePlans) { plan in
                                planRow(plan)
                            }
                        }
                        .padding()
                    }
                }
            }
            .background(LujianPalette.paperDeep.opacity(0.45))
            .navigationTitle(mode.title)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        ForEach(BoardMode.allCases, id: \.self) { value in
                            Button(value.title) { mode = value }
                        }
                    } label: {
                        Label(mode.title, systemImage: "chevron.down")
                    }
                    .accessibilityIdentifier("旅笺板切换")
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if mode == .active && !isManaging {
                        Button("导入", systemImage: "plus", action: onImport)
                            .accessibilityIdentifier("导入计划")
                    }
                    Button(isManaging ? "完成" : "管理") {
                        isManaging.toggle()
                        if !isManaging { selection.removeAll() }
                    }
                    .accessibilityIdentifier("管理计划")
                }
            }
            .safeAreaInset(edge: .bottom) {
                if isManaging { managementBar }
            }
            .onChange(of: mode) {
                selection = BoardSelectionPolicy.retain(selection, in: visiblePlans)
            }
            .onChange(of: visiblePlans.map(\.id)) {
                selection = BoardSelectionPolicy.retain(selection, in: visiblePlans)
            }
            .confirmationDialog(
                "删除 \(selection.count) 个计划？",
                isPresented: $confirmsDeletion,
                titleVisibility: .visible
            ) {
                Button("删除私有副本", role: .destructive) { perform { try store.delete(ids: selection) } }
                Button("取消", role: .cancel) {}
            } message: {
                Text("只删除旅笺中的计划和私有媒体，不影响导入源文件与系统相册。")
            }
            .alert("操作结果", isPresented: operationAlertBinding) {
                Button("知道了", role: .cancel) { operationMessage = nil }
            } message: { Text(operationMessage ?? "") }
        }
    }

    @ViewBuilder
    private func planRow(_ plan: TravelPlan) -> some View {
        if isManaging {
            Button { toggle(plan.id) } label: {
                PlanBoardCard(plan: plan, selected: selection.contains(plan.id))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("选择计划-\(plan.title)")
        } else {
            NavigationLink {
                PlanDetailView(planID: plan.id, store: store)
            } label: {
                PlanBoardCard(plan: plan, selected: false)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("计划卡片-\(plan.title)")
            .accessibilityLabel("打开计划，\(plan.title)")
        }
    }

    private var managementBar: some View {
        HStack(spacing: 10) {
            Button(selection.count == visiblePlans.count ? "取消全选" : "全选") {
                selection = selection.count == visiblePlans.count
                    ? []
                    : BoardSelectionPolicy.selectAll(plans: store.plans, mode: mode)
            }
            .buttonStyle(.bordered)

            Spacer()

            Button(mode == .active ? "归档" : "恢复") {
                perform {
                    if mode == .active { try store.archive(ids: selection) }
                    else { try store.restore(ids: selection) }
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(selection.isEmpty)

            Button("删除", role: .destructive) { confirmsDeletion = true }
                .buttonStyle(.bordered)
                .disabled(selection.isEmpty)
        }
        .padding()
        .background(.bar)
    }

    private var operationAlertBinding: Binding<Bool> {
        Binding(get: { operationMessage != nil }, set: { if !$0 { operationMessage = nil } })
    }

    private func toggle(_ id: UUID) {
        if selection.contains(id) { selection.remove(id) } else { selection.insert(id) }
    }

    private func perform(_ operation: () throws -> Void) {
        do {
            try operation()
            selection.removeAll()
        } catch {
            operationMessage = error.localizedDescription
        }
    }
}

private struct PlanBoardCard: View {
    let plan: TravelPlan
    let selected: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: plan.isArchived ? "checkmark.seal.fill" : "mappin.and.ellipse")
                .font(.title2)
                .foregroundStyle(plan.isArchived ? LujianPalette.gold : LujianPalette.coral)
                .frame(width: 46, height: 46)
                .background(LujianPalette.paperDeep, in: Circle())

            VStack(alignment: .leading, spacing: 5) {
                Text(plan.title).font(.headline).foregroundStyle(LujianPalette.ink)
                Text(plan.dateRange ?? plan.destinations.map(\.name).joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text("\(plan.days.count) 天 · \(plan.photos.count) 张照片")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: selected ? "checkmark.circle.fill" : "chevron.right")
                .foregroundStyle(selected ? LujianPalette.coral : .secondary)
        }
        .paperCard()
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(selected ? LujianPalette.coral : .clear, lineWidth: 3)
        }
    }
}
