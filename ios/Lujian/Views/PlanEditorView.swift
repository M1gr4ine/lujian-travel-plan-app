import PhotosUI
import SwiftUI

struct PlanEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: TravelPlan
    @State private var coverItem: PhotosPickerItem?
    @State private var coverData: Data?
    @State private var removesCover = false
    @State private var message: String?
    let onSave: (TravelPlan, Data?, Bool) throws -> Void

    init(plan: TravelPlan, onSave: @escaping (TravelPlan, Data?, Bool) throws -> Void) {
        _draft = State(initialValue: plan)
        self.onSave = onSave
    }

    var body: some View {
        Form {
            Section("基本信息") {
                TextField("计划标题", text: $draft.title)
                TextField("日期范围", text: optionalText(\.dateRange))
                TextField("同行人数", text: optionalText(\.travelers))
                TextField("旅行风格", text: optionalText(\.style))
                TextField("住宿区域", text: optionalText(\.baseArea))
                TextField("总预算", text: optionalText(\.budget))
                TextField("住宿预算", text: optionalText(\.accommodationBudget))
            }

            Section("预览图") {
                PhotosPicker(selection: $coverItem, matching: .images) {
                    Label(draft.coverRelativePath == nil ? "选择预览图" : "更换预览图", systemImage: "photo")
                }
                if draft.coverRelativePath != nil {
                    Button("恢复默认预览图", role: .destructive) {
                        draft.coverRelativePath = nil
                        coverData = nil
                        coverItem = nil
                        removesCover = true
                    }
                }
            }

            Section("目的地") {
                ForEach(draft.destinations.indices, id: \.self) { index in
                    TextField("目的地", text: $draft.destinations[index].name)
                }
                .onDelete { draft.destinations.remove(atOffsets: $0) }
                Button("添加目的地", systemImage: "plus") {
                    draft.destinations.append(PlanDestination(name: "新目的地"))
                }
            }

            Section("每日行程") {
                ForEach(draft.days.indices, id: \.self) { dayIndex in
                    DisclosureGroup {
                        TextField("日期标签", text: $draft.days[dayIndex].label)
                        TextField("当日标题", text: $draft.days[dayIndex].title)
                        TextField("预算", text: optionalDayText(dayIndex, \.budget))
                        ForEach(draft.days[dayIndex].items.indices, id: \.self) { itemIndex in
                            VStack(alignment: .leading) {
                                TextField("时间", text: optionalItemText(dayIndex, itemIndex, \.time))
                                TextField("行程", text: $draft.days[dayIndex].items[itemIndex].title)
                                TextField("分类", text: optionalItemText(dayIndex, itemIndex, \.category))
                                TextField("费用", text: optionalItemText(dayIndex, itemIndex, \.cost))
                                TextField("备注", text: optionalItemText(dayIndex, itemIndex, \.notes), axis: .vertical)
                            }
                            .padding(.vertical, 4)
                        }
                        .onDelete { draft.days[dayIndex].items.remove(atOffsets: $0) }
                        Button("添加行程", systemImage: "plus") {
                            draft.days[dayIndex].items.append(
                                PlanItem(id: UUID().uuidString, time: "09:00", title: "新行程", category: "行程", notes: "")
                            )
                        }
                    } label: {
                        Text("\(draft.days[dayIndex].label) · \(draft.days[dayIndex].title)")
                    }
                }
                .onDelete { draft.days.remove(atOffsets: $0) }
                Button("添加一天", systemImage: "calendar.badge.plus") {
                    let number = draft.days.count + 1
                    draft.days.append(PlanDay(id: "day-\(UUID().uuidString)", label: "第\(number)天", title: "新行程"))
                }
            }
        }
        .navigationTitle("编辑计划")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button("保存") {
                    guard !draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                        message = "计划标题不能为空"
                        return
                    }
                    do {
                        try onSave(draft, coverData, removesCover)
                        dismiss()
                    } catch {
                        message = error.localizedDescription
                    }
                }
            }
        }
        .onChange(of: coverItem) {
            Task {
                if let data = try? await coverItem?.loadTransferable(type: Data.self) {
                    coverData = data
                    removesCover = false
                    message = "预览图已选取，保存后写入旅笺私有目录（\(data.count / 1024) KB）"
                }
            }
        }
        .alert("提示", isPresented: messageBinding) {
            Button("知道了", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { message != nil }, set: { if !$0 { message = nil } })
    }

    private func optionalText(_ keyPath: WritableKeyPath<TravelPlan, String?>) -> Binding<String> {
        Binding(get: { draft[keyPath: keyPath] ?? "" }, set: { draft[keyPath: keyPath] = $0.nilIfBlank })
    }

    private func optionalDayText(_ day: Int, _ keyPath: WritableKeyPath<PlanDay, String?>) -> Binding<String> {
        Binding(
            get: { draft.days[day][keyPath: keyPath] ?? "" },
            set: { draft.days[day][keyPath: keyPath] = $0.nilIfBlank }
        )
    }

    private func optionalItemText(
        _ day: Int,
        _ item: Int,
        _ keyPath: WritableKeyPath<PlanItem, String?>
    ) -> Binding<String> {
        Binding(
            get: { draft.days[day].items[item][keyPath: keyPath] ?? "" },
            set: { draft.days[day].items[item][keyPath: keyPath] = $0.nilIfBlank }
        )
    }
}

private extension String {
    var nilIfBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
