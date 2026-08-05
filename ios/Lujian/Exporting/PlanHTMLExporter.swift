import Foundation

enum PlanHTMLExporter {
    static func data(for plan: TravelPlan) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let jsonData = try encoder.encode(ExportPayload(plan: plan))
        guard var json = String(data: jsonData, encoding: .utf8) else {
            throw HTMLImportError.invalidHTML
        }
        json = json.replacingOccurrences(of: "</", with: "<\\/")
        let title = escapeHTML(plan.title)
        let html = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>\(title)</title>
          <style>
            :root{color-scheme:light}body{margin:0;background:#FAF6EF;color:#2A2520;font:17px/1.6 -apple-system,BlinkMacSystemFont,sans-serif}main{max-width:760px;margin:auto;padding:28px 20px 60px}h1{font-size:32px;line-height:1.15}.day{margin:18px 0;padding:18px;border:2px solid #2A2520;border-radius:18px;background:#FFFDF8}.item{padding:10px 0;border-top:1px solid #DED5C7}.time{color:#B85F52;font-weight:700}
          </style>
          <script id="lujian-plan" type="application/json">\(json)</script>
        </head>
        <body><main><h1>\(title)</h1>\(renderDays(plan.days))</main></body>
        </html>
        """
        return Data(html.utf8)
    }

    private static func renderDays(_ days: [PlanDay]) -> String {
        days.map { day in
            let items = day.items.map { item in
                let time = item.time.map { "<span class=\"time\">\(escapeHTML($0))</span> " } ?? ""
                let notes = item.notes.map { "<div>\(escapeHTML($0))</div>" } ?? ""
                return "<div class=\"item\">\(time)<strong>\(escapeHTML(item.title))</strong>\(notes)</div>"
            }.joined()
            return "<section class=\"day\"><h2>\(escapeHTML(day.label)) · \(escapeHTML(day.title))</h2>\(items)</section>"
        }.joined()
    }

    private static func escapeHTML(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&#39;")
    }
}

private struct ExportPayload: Encodable {
    var schemaVersion = AppMetadata.storeSchema
    var title: String
    var destinations: [PlanDestination]
    var days: [ExportDay]
    var places: [ExportPlace]
    var sections: [ExportSection]
    var dateRange: String?
    var travelers: String?
    var style: String?
    var baseArea: String?
    var budget: String?
    var assumptions: [String]
    var trip: ExportTrip?

    init(plan: TravelPlan) {
        title = plan.title
        destinations = plan.destinations
        days = plan.days.map(ExportDay.init)
        places = plan.places.map(ExportPlace.init)
        sections = plan.sections.map { ExportSection(title: $0.title, content: $0.content) }
        dateRange = plan.dateRange
        travelers = plan.travelers
        style = plan.style
        baseArea = plan.baseArea
        budget = plan.budget
        assumptions = plan.assumptions
        trip = plan.accommodationBudget.map { ExportTrip(accommodationBudget: $0) }
    }
}

private struct ExportDay: Encodable {
    var id: String
    var label: String
    var title: String
    var items: [ExportItem]
    var summary: String?
    var budget: String?
    var backup: String?
    var distanceEstimate: String?
    var durationEstimate: String?
    var mapStops: [ExportMapStop]
    var mapLegs: [ExportMapLeg]

    init(_ day: PlanDay) {
        id = day.id
        label = day.label
        title = day.title
        items = day.items.map(ExportItem.init)
        summary = day.summary
        budget = day.budget
        backup = day.backup
        distanceEstimate = day.distanceEstimate
        durationEstimate = day.durationEstimate
        mapStops = day.mapStops.map(ExportMapStop.init)
        mapLegs = day.mapLegs.map(ExportMapLeg.init)
    }
}

private struct ExportItem: Encodable {
    var id: String
    var time: String
    var title: String
    var category: String
    var cost: String?
    var notes: String
    var placeID: String?
    var transport: String?
    var mapLinks: PlanMapLinks

    enum CodingKeys: String, CodingKey {
        case id, time, title, category, cost, notes, transport, mapLinks
        case placeID = "placeId"
    }

    init(_ item: PlanItem) {
        id = item.id
        time = item.time ?? "待定"
        title = item.title
        category = item.category ?? "行程"
        cost = item.cost
        notes = item.notes ?? ""
        placeID = item.placeID
        transport = item.transport
        mapLinks = item.mapLinks
    }
}

private struct ExportMapStop: Encodable {
    var id: String
    var title: String
    var time: String?
    var category: String?
    var latitude: Double?
    var longitude: Double?

    init(_ stop: PlanMapStop) {
        id = stop.id
        title = stop.title
        time = stop.time
        category = stop.category
        latitude = stop.latitude
        longitude = stop.longitude
    }
}

private struct ExportMapLeg: Encodable {
    var id: String
    var from: String
    var to: String
    var mode: String?
    var summary: String?

    init(_ leg: PlanMapLeg) {
        id = leg.id
        from = leg.fromID
        to = leg.toID
        mode = leg.mode
        summary = leg.summary
    }
}

private struct ExportPlace: Encodable {
    var id: String
    var name: String
    var address: String?
    var latitude: Double?
    var longitude: Double?
    var mapLinks: PlanMapLinks

    init(_ place: PlanPlace) {
        id = place.id
        name = place.name
        address = place.address
        latitude = place.latitude
        longitude = place.longitude
        mapLinks = place.mapLinks
    }
}

private struct ExportSection: Encodable {
    var title: String
    var content: String
}

private struct ExportTrip: Encodable {
    var accommodationBudget: String
}
