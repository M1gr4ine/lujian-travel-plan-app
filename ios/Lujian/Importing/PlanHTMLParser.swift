import Foundation

struct ParsedImport {
    var plan: TravelPlan
    var isEnhanced: Bool
}

enum PlanHTMLParser {
    static func parse(_ html: DecodedHTML) throws -> ParsedImport {
        let blocks = enhancedBlocks(in: html.text)
        if !blocks.isEmpty {
            guard blocks.count == 1 else {
                throw HTMLImportError.incompatibleContract("必须且只能包含一个 lujian-plan 元数据块")
            }
            let block = blocks[0]
            guard attribute("type", in: block.attributes)?.lowercased() == "application/json" else {
                throw HTMLImportError.incompatibleContract("元数据块必须使用 application/json")
            }
            return try parseEnhanced(block.body, fileName: html.fileName)
        }
        return try parseOrdinary(html.text, fileName: html.fileName)
    }

    private static func parseEnhanced(_ json: String, fileName: String) throws -> ParsedImport {
        let payload: EnhancedPayload
        do {
            payload = try JSONDecoder().decode(EnhancedPayload.self, from: Data(json.utf8))
        } catch {
            throw HTMLImportError.incompatibleContract("JSON 无效：\(error.localizedDescription)")
        }
        guard payload.schemaVersion == AppMetadata.storeSchema else {
            throw HTMLImportError.incompatibleContract("schemaVersion 必须为数字 1")
        }
        guard !payload.title.trimmed.isEmpty else {
            throw HTMLImportError.incompatibleContract("title 不能为空")
        }
        guard !payload.destinations.isEmpty else {
            throw HTMLImportError.incompatibleContract("destinations 必须是非空数组")
        }
        guard !payload.days.isEmpty else {
            throw HTMLImportError.incompatibleContract("days 必须是非空数组")
        }

        let destinations = try payload.destinations.map { destination -> PlanDestination in
            guard !destination.name.trimmed.isEmpty else {
                throw HTMLImportError.incompatibleContract("目的地名称不能为空")
            }
            return PlanDestination(
                name: destination.name,
                countryCode: destination.countryCode,
                latitude: validLatitude(destination.latitude),
                longitude: validLongitude(destination.longitude)
            )
        }
        let days = try payload.days.map { day -> PlanDay in
            guard !day.id.trimmed.isEmpty, !day.label.trimmed.isEmpty, !day.title.trimmed.isEmpty else {
                throw HTMLImportError.incompatibleContract("日期 id、label、title 不能为空")
            }
            let items = try day.items.map { item -> PlanItem in
                guard !item.id.trimmed.isEmpty,
                      !item.time.trimmed.isEmpty,
                      !item.title.trimmed.isEmpty,
                      !item.category.trimmed.isEmpty else {
                    throw HTMLImportError.incompatibleContract("行程 id、time、title、category 不能为空")
                }
                return PlanItem(
                    id: item.id,
                    time: item.time,
                    title: item.title,
                    category: item.category,
                    cost: item.cost,
                    notes: item.notes,
                    placeID: item.placeID,
                    transport: item.transport,
                    mapLinks: item.mapLinks?.model ?? PlanMapLinks()
                )
            }
            return PlanDay(
                id: day.id,
                label: day.label,
                title: day.title,
                items: items,
                summary: day.summary,
                budget: day.budget,
                backup: day.backup,
                distanceEstimate: day.distanceEstimate,
                durationEstimate: day.durationEstimate,
                mapStops: day.mapStops.enumerated().map { index, stop in
                    PlanMapStop(
                        id: stop.id ?? "\(day.id)-map-\(index)",
                        title: stop.title ?? stop.name ?? "地点",
                        time: stop.time ?? stop.meta,
                        category: stop.category ?? stop.kind,
                        latitude: validLatitude(stop.latitude ?? stop.coordinates?.latitude ?? stop.coordinates?.lat),
                        longitude: validLongitude(
                            stop.longitude ?? stop.coordinates?.longitude ?? stop.coordinates?.lng ?? stop.coordinates?.lon
                        )
                    )
                },
                mapLegs: day.mapLegs.compactMap { leg in
                    guard let from = leg.from, !from.isEmpty, let to = leg.to, !to.isEmpty else { return nil }
                    return PlanMapLeg(
                        id: leg.id ?? "\(from)-\(to)",
                        fromID: from,
                        toID: to,
                        mode: leg.mode,
                        summary: leg.summary ?? leg.transport
                    )
                }
            )
        }
        let places = payload.places.enumerated().map { index, place in
            PlanPlace(
                id: place.id ?? "place-\(index)",
                name: place.name,
                address: place.address,
                latitude: validLatitude(place.latitude ?? place.coordinates?.latitude ?? place.coordinates?.lat),
                longitude: validLongitude(
                    place.longitude ?? place.coordinates?.longitude ?? place.coordinates?.lng ?? place.coordinates?.lon
                ),
                mapLinks: place.mapLinks?.model ?? PlanMapLinks()
            )
        }
        let sections = payload.sections.map { PlanSection(title: $0.title, content: $0.content) }

        return ParsedImport(
            plan: TravelPlan(
                title: payload.title,
                capability: .enhanced,
                destinations: destinations,
                days: days,
                sections: sections,
                dateRange: payload.dateRange,
                travelers: payload.travelers,
                style: payload.style,
                baseArea: payload.baseArea,
                budget: payload.budget,
                accommodationBudget: payload.trip?.accommodationBudget ?? payload.trip?.hotelBudget,
                assumptions: payload.assumptions,
                places: places,
                sourceName: fileName,
                sourcePayloadJSON: json
            ),
            isEnhanced: true
        )
    }

    private static func parseOrdinary(_ html: String, fileName: String) throws -> ParsedImport {
        let title = firstCapture(#"(?is)<title\b[^>]*>(.*?)</title\s*>"#, in: html)
            .map(stripTagsAndDecodeEntities)?.trimmed
        let displayTitle = title?.isEmpty == false ? title! : (fileName as NSString).deletingPathExtension
        guard !displayTitle.trimmed.isEmpty else { throw HTMLImportError.invalidHTML }

        let destinationName = metaContent(
            names: ["lujian:destination", "travel:destination", "geo.placename"],
            in: html
        )
        let countryCode = metaContent(names: ["lujian:country-code"], in: html)
        let coordinateText = metaContent(names: ["geo.position", "icbm"], in: html)
        let coordinates = coordinateText.flatMap(parseCoordinates) ?? explicitCoordinates(in: html)
        let destinations = destinationName.map {
            [
                PlanDestination(
                    name: $0,
                    countryCode: countryCode,
                    latitude: coordinates?.0,
                    longitude: coordinates?.1
                )
            ]
        } ?? []

        return ParsedImport(
            plan: TravelPlan(
                title: displayTitle,
                capability: .viewOnly,
                destinations: destinations,
                sourceName: fileName
            ),
            isEnhanced: false
        )
    }

    private static func enhancedBlocks(in html: String) -> [(attributes: String, body: String)] {
        guard let regex = try? NSRegularExpression(
            pattern: #"<script\b([^>]*)>([\s\S]*?)</script\s*>"#,
            options: .caseInsensitive
        ) else { return [] }
        let range = NSRange(html.startIndex..., in: html)
        return regex.matches(in: html, range: range).compactMap { match in
            guard let attributesRange = Range(match.range(at: 1), in: html),
                  let bodyRange = Range(match.range(at: 2), in: html) else { return nil }
            let attributes = String(html[attributesRange])
            guard attribute("id", in: attributes)?.lowercased() == "lujian-plan" else { return nil }
            return (attributes, String(html[bodyRange]))
        }
    }

    private static func attribute(_ name: String, in attributes: String) -> String? {
        firstCapture("(?i)\\b\(NSRegularExpression.escapedPattern(for: name))\\s*=\\s*[\"']([^\"']*)[\"']", in: attributes)
    }

    private static func metaContent(names: [String], in html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"<meta\b[^>]*>"#, options: .caseInsensitive) else {
            return nil
        }
        let tags = regex.matches(in: html, range: NSRange(html.startIndex..., in: html)).compactMap {
            Range($0.range, in: html).map { String(html[$0]) }
        }
        for tag in tags {
            let name = attribute("name", in: tag)?.lowercased()
            if let name, names.map({ $0.lowercased() }).contains(name), let content = attribute("content", in: tag) {
                return content.trimmed
            }
        }
        return nil
    }

    private static func parseCoordinates(_ value: String) -> (Double, Double)? {
        let parts = value.split(whereSeparator: { $0 == ";" || $0 == "," }).map(String.init)
        guard parts.count >= 2,
              let latitude = Double(parts[0].trimmed),
              let longitude = Double(parts[1].trimmed),
              validLatitude(latitude) != nil,
              validLongitude(longitude) != nil else { return nil }
        return (latitude, longitude)
    }

    private static func explicitCoordinates(in html: String) -> (Double, Double)? {
        guard let latitudeText = metaContent(names: ["lujian:latitude"], in: html),
              let longitudeText = metaContent(names: ["lujian:longitude"], in: html),
              let latitude = Double(latitudeText),
              let longitude = Double(longitudeText),
              validLatitude(latitude) != nil,
              validLongitude(longitude) != nil else { return nil }
        return (latitude, longitude)
    }

    private static func firstCapture(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }

    private static func stripTagsAndDecodeEntities(_ value: String) -> String {
        value.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
    }

    private static func validLatitude(_ value: Double?) -> Double? {
        value.flatMap { (-90.0...90.0).contains($0) ? $0 : nil }
    }

    private static func validLongitude(_ value: Double?) -> Double? {
        value.flatMap { (-180.0...180.0).contains($0) ? $0 : nil }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

private struct EnhancedPayload: Decodable {
    var schemaVersion: Int
    var title: String
    var destinations: [DestinationDTO]
    var days: [DayDTO]
    var places: [PlaceDTO] = []
    var sections: [SectionDTO] = []
    var dateRange: String?
    var travelers: String?
    var style: String?
    var baseArea: String?
    var budget: String?
    var assumptions: [String] = []
    var trip: TripDTO?

    enum CodingKeys: String, CodingKey {
        case schemaVersion, title, destinations, days, places, sections, dateRange, travelers
        case style, baseArea, budget, assumptions, trip
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try values.decode(Int.self, forKey: .schemaVersion)
        title = try values.decode(String.self, forKey: .title)
        destinations = try values.decode([DestinationDTO].self, forKey: .destinations)
        days = try values.decode([DayDTO].self, forKey: .days)
        places = try values.decodeIfPresent([PlaceDTO].self, forKey: .places) ?? []
        sections = try values.decodeIfPresent([SectionDTO].self, forKey: .sections) ?? []
        dateRange = try values.decodeIfPresent(String.self, forKey: .dateRange)
        travelers = try values.decodeIfPresent(String.self, forKey: .travelers)
        style = try values.decodeIfPresent(String.self, forKey: .style)
        baseArea = try values.decodeIfPresent(String.self, forKey: .baseArea)
        budget = try values.decodeIfPresent(String.self, forKey: .budget)
        assumptions = try values.decodeIfPresent([String].self, forKey: .assumptions) ?? []
        trip = try values.decodeIfPresent(TripDTO.self, forKey: .trip)
    }
}

private struct DestinationDTO: Decodable {
    var name: String
    var countryCode: String?
    var latitude: Double?
    var longitude: Double?

    enum CodingKeys: String, CodingKey { case name, countryCode, latitude, longitude }

    init(from decoder: Decoder) throws {
        if let single = try? decoder.singleValueContainer(), let name = try? single.decode(String.self) {
            self.name = name
            return
        }
        let values = try decoder.container(keyedBy: CodingKeys.self)
        name = try values.decode(String.self, forKey: .name)
        countryCode = try values.decodeIfPresent(String.self, forKey: .countryCode)
        latitude = try values.decodeIfPresent(Double.self, forKey: .latitude)
        longitude = try values.decodeIfPresent(Double.self, forKey: .longitude)
    }
}

private struct DayDTO: Decodable {
    var id: String
    var label: String
    var title: String
    var items: [ItemDTO]
    var summary: String?
    var budget: String?
    var backup: String?
    var distanceEstimate: String?
    var durationEstimate: String?
    var mapStops: [MapStopDTO] = []
    var mapLegs: [MapLegDTO] = []

    enum CodingKeys: String, CodingKey {
        case id, label, title, items, summary, budget, backup
        case distanceEstimate, durationEstimate, mapStops, mapLegs
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        label = try values.decode(String.self, forKey: .label)
        title = try values.decode(String.self, forKey: .title)
        items = try values.decode([ItemDTO].self, forKey: .items)
        summary = try values.decodeIfPresent(String.self, forKey: .summary)
        budget = try values.decodeIfPresent(String.self, forKey: .budget)
        backup = try values.decodeIfPresent(String.self, forKey: .backup)
        distanceEstimate = try values.decodeIfPresent(String.self, forKey: .distanceEstimate)
        durationEstimate = try values.decodeIfPresent(String.self, forKey: .durationEstimate)
        mapStops = try values.decodeIfPresent([MapStopDTO].self, forKey: .mapStops) ?? []
        mapLegs = try values.decodeIfPresent([MapLegDTO].self, forKey: .mapLegs) ?? []
    }
}

private struct ItemDTO: Decodable {
    var id: String
    var time: String
    var title: String
    var category: String
    var cost: String?
    var notes: String
    var placeID: String?
    var transport: String?
    var mapLinks: MapLinksDTO?

    enum CodingKeys: String, CodingKey {
        case id, time, title, category, cost, notes, transport, mapLinks
        case placeID = "placeId"
    }
}

private struct MapLinksDTO: Decodable {
    var amap: String?
    var baidu: String?
    var model: PlanMapLinks { PlanMapLinks(amap: amap, baidu: baidu) }
}

private struct CoordinatesDTO: Decodable {
    var latitude: Double?
    var longitude: Double?
    var lat: Double?
    var lng: Double?
    var lon: Double?
}

private struct MapStopDTO: Decodable {
    var id: String?
    var title: String?
    var name: String?
    var time: String?
    var meta: String?
    var category: String?
    var kind: String?
    var latitude: Double?
    var longitude: Double?
    var coordinates: CoordinatesDTO?
}

private struct MapLegDTO: Decodable {
    var id: String?
    var from: String?
    var to: String?
    var mode: String?
    var summary: String?
    var transport: String?
}

private struct PlaceDTO: Decodable {
    var id: String?
    var name: String
    var address: String?
    var latitude: Double?
    var longitude: Double?
    var coordinates: CoordinatesDTO?
    var mapLinks: MapLinksDTO?
}

private struct SectionDTO: Decodable {
    var title: String
    var content: String
}

private struct TripDTO: Decodable {
    var accommodationBudget: String?
    var hotelBudget: String?
}
