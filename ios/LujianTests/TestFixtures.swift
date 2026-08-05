import Foundation
@testable import Lujian

enum TestFixtures {
    static func temporaryRoot() throws -> URL {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("LujianTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    static func plan(
        title: String = "大连五日旅行计划",
        archived: Bool = false,
        id: UUID = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    ) -> TravelPlan {
        TravelPlan(
            id: id,
            title: title,
            capability: .enhanced,
            destinations: [
                PlanDestination(name: "大连", countryCode: "CN", latitude: 38.914, longitude: 121.6147)
            ],
            days: [
                PlanDay(
                    id: "day-1",
                    label: "第一天",
                    title: "海边漫步",
                    items: [
                        PlanItem(
                            id: "item-1",
                            time: "09:00",
                            title: "星海广场",
                            category: "景点",
                            cost: "免费",
                            notes: "从酒店出发",
                            placeID: "place-1"
                        )
                    ]
                )
            ],
            places: [
                PlanPlace(
                    id: "place-1",
                    name: "星海广场",
                    address: "大连市沙河口区",
                    latitude: 38.8812,
                    longitude: 121.5881
                )
            ],
            sourceName: "fixture.html",
            isArchived: archived
        )
    }

    static func html(title: String = "大连五日旅行计划", schemaVersion: Int = 1) -> String {
        """
        <!doctype html>
        <html lang="zh-CN">
        <head><meta charset="utf-8"><title>\(title)</title></head>
        <body>
        <script id="lujian-plan" type="application/json">
        {"schemaVersion":\(schemaVersion),"title":"\(title)","destinations":[{"name":"大连","countryCode":"CN","latitude":38.914,"longitude":121.6147}],"days":[{"id":"day-1","label":"第一天","title":"海边漫步","items":[{"id":"item-1","time":"09:00","title":"星海广场","category":"景点","cost":"免费","notes":"从酒店出发","placeId":"place-1"}]}],"places":[{"id":"place-1","name":"星海广场","address":"大连市沙河口区","latitude":38.8812,"longitude":121.5881}]}
        </script>
        </body>
        </html>
        """
    }
}
