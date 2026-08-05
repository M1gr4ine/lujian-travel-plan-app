import SwiftUI

enum DemoPlanLoader {
    @MainActor
    static func importIfNeeded(store: PlanStore) throws {
        guard !store.plans.contains(where: { $0.sourceName == "DemoPlan.html" }) else { return }
        if let url = Bundle.main.url(forResource: "DemoPlan", withExtension: "html") {
            _ = try PlanImportService(store: store).importData(
                Data(contentsOf: url),
                fileName: "DemoPlan.html"
            )
        } else {
            _ = try PlanImportService(store: store).importData(
                Data(embeddedDemoHTML.utf8),
                fileName: "DemoPlan.html"
            )
        }
    }

    private static let embeddedDemoHTML = """
    <!doctype html><html><head><meta charset="utf-8"><title>大连五日旅行计划</title></head><body>
    <script id="lujian-plan" type="application/json">
    {"schemaVersion":1,"title":"大连五日旅行计划","dateRange":"9月25日—9月29日","travelers":"2 人","style":"海滨慢旅行","baseArea":"中山区","budget":"预计 3000 元","destinations":[{"name":"大连","countryCode":"CN","latitude":38.914,"longitude":121.6147}],"days":[{"id":"day-1","label":"第一天","title":"海边与老城","distanceEstimate":"18 公里","durationEstimate":"约 1 小时","items":[{"id":"item-1","time":"09:00","title":"酒店出发","category":"住宿","notes":"吃完早餐再出发","placeId":"hotel"},{"id":"item-2","time":"10:00","title":"星海广场","category":"景点","cost":"免费","notes":"沿海步行","placeId":"xinghai"},{"id":"item-3","time":"18:00","title":"返回酒店","category":"住宿","notes":"结束当天行程","placeId":"hotel"}],"mapStops":[{"id":"hotel-start","title":"酒店出发","latitude":38.921,"longitude":121.639},{"id":"xinghai","title":"星海广场","latitude":38.8812,"longitude":121.5881},{"id":"hotel-end","title":"返回酒店","latitude":38.921,"longitude":121.639}]}],"places":[{"id":"hotel","name":"中山广场酒店","latitude":38.921,"longitude":121.639},{"id":"xinghai","name":"星海广场","latitude":38.8812,"longitude":121.5881}]}
    </script></body></html>
    """
}

struct ProfileView: View {
    @ObservedObject var store: PlanStore
    @State private var message: String?

    private var summary: StorageSummary { store.storageSummary }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        Image(systemName: "map.fill")
                            .font(.largeTitle)
                            .foregroundStyle(LujianPalette.coral)
                            .frame(width: 58, height: 58)
                            .background(LujianPalette.paperDeep, in: RoundedRectangle(cornerRadius: 16))
                        VStack(alignment: .leading) {
                            Text("旅笺").font(.title2.bold())
                            Text("iOS \(AppMetadata.version) (\(AppMetadata.build))")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 6)
                }

                Section("本地存储") {
                    LabeledContent("旅行计划", value: "\(summary.planCount)")
                    LabeledContent("私有照片", value: "\(summary.photoCount)")
                    LabeledContent("占用空间", value: ByteCountFormatter.string(fromByteCount: summary.privateBytes, countStyle: .file))
                }

                if let recoveryIssue = store.recoveryIssue {
                    Section("数据恢复") {
                        Label(recoveryIssue, systemImage: "exclamationmark.shield")
                            .foregroundStyle(LujianPalette.coral)
                    }
                }

                Section("快速体验") {
                    Button("导入大连演示计划", systemImage: "sparkles") {
                        do {
                            try DemoPlanLoader.importIfNeeded(store: store)
                            message = "演示计划已在旅笺板中"
                        } catch {
                            message = error.localizedDescription
                        }
                    }
                }

                Section("隐私") {
                    Label("计划与照片仅保存在应用私有目录", systemImage: "lock.shield")
                    Label("删除照片不影响系统相册原图", systemImage: "photo.badge.checkmark")
                    Label("没有账号、业务云同步或原生脚本桥", systemImage: "person.crop.circle.badge.xmark")
                }
            }
            .scrollContentBackground(.hidden)
            .background(LujianPalette.paperDeep.opacity(0.35))
            .navigationTitle("我")
            .alert("旅笺", isPresented: messageBinding) {
                Button("知道了", role: .cancel) { message = nil }
            } message: { Text(message ?? "") }
        }
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { message != nil }, set: { if !$0 { message = nil } })
    }
}
