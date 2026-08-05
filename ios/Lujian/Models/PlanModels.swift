import Foundation

enum PlanCapability: String, Codable, Equatable {
    case enhanced
    case viewOnly
}

struct PlanMapLinks: Codable, Equatable, Hashable {
    var amap: String?
    var baidu: String?

    init(amap: String? = nil, baidu: String? = nil) {
        self.amap = amap
        self.baidu = baidu
    }
}

struct PlanDestination: Codable, Equatable, Identifiable {
    var id: String { "\(name)|\(latitude ?? 0)|\(longitude ?? 0)" }
    var name: String
    var countryCode: String?
    var latitude: Double?
    var longitude: Double?

    init(
        name: String,
        countryCode: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil
    ) {
        self.name = name
        self.countryCode = countryCode
        self.latitude = latitude
        self.longitude = longitude
    }
}

struct PlanItem: Codable, Equatable, Identifiable {
    var id: String
    var time: String?
    var title: String
    var category: String?
    var cost: String?
    var notes: String?
    var placeID: String?
    var transport: String?
    var mapLinks: PlanMapLinks

    init(
        id: String,
        time: String? = nil,
        title: String,
        category: String? = nil,
        cost: String? = nil,
        notes: String? = nil,
        placeID: String? = nil,
        transport: String? = nil,
        mapLinks: PlanMapLinks = PlanMapLinks()
    ) {
        self.id = id
        self.time = time
        self.title = title
        self.category = category
        self.cost = cost
        self.notes = notes
        self.placeID = placeID
        self.transport = transport
        self.mapLinks = mapLinks
    }
}

struct PlanMapStop: Codable, Equatable, Identifiable {
    var id: String
    var title: String
    var time: String?
    var category: String?
    var latitude: Double?
    var longitude: Double?

    init(
        id: String,
        title: String,
        time: String? = nil,
        category: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil
    ) {
        self.id = id
        self.title = title
        self.time = time
        self.category = category
        self.latitude = latitude
        self.longitude = longitude
    }
}

struct PlanMapLeg: Codable, Equatable, Identifiable {
    var id: String
    var fromID: String
    var toID: String
    var mode: String?
    var summary: String?
}

struct PlanDay: Codable, Equatable, Identifiable {
    var id: String
    var label: String
    var title: String
    var items: [PlanItem]
    var summary: String?
    var budget: String?
    var backup: String?
    var distanceEstimate: String?
    var durationEstimate: String?
    var mapStops: [PlanMapStop]
    var mapLegs: [PlanMapLeg]

    init(
        id: String,
        label: String,
        title: String,
        items: [PlanItem] = [],
        summary: String? = nil,
        budget: String? = nil,
        backup: String? = nil,
        distanceEstimate: String? = nil,
        durationEstimate: String? = nil,
        mapStops: [PlanMapStop] = [],
        mapLegs: [PlanMapLeg] = []
    ) {
        self.id = id
        self.label = label
        self.title = title
        self.items = items
        self.summary = summary
        self.budget = budget
        self.backup = backup
        self.distanceEstimate = distanceEstimate
        self.durationEstimate = durationEstimate
        self.mapStops = mapStops
        self.mapLegs = mapLegs
    }
}

struct PlanPlace: Codable, Equatable, Identifiable {
    var id: String
    var name: String
    var address: String?
    var latitude: Double?
    var longitude: Double?
    var mapLinks: PlanMapLinks

    init(
        id: String,
        name: String,
        address: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        mapLinks: PlanMapLinks = PlanMapLinks()
    ) {
        self.id = id
        self.name = name
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
        self.mapLinks = mapLinks
    }
}

struct PlanSection: Codable, Equatable, Identifiable {
    var id: UUID
    var title: String
    var content: String

    init(id: UUID = UUID(), title: String, content: String) {
        self.id = id
        self.title = title
        self.content = content
    }
}

struct PlanPhoto: Codable, Equatable, Identifiable {
    var id: UUID
    var placeID: String?
    var relativePath: String
    var createdAt: Date

    init(id: UUID = UUID(), placeID: String? = nil, relativePath: String, createdAt: Date = Date()) {
        self.id = id
        self.placeID = placeID
        self.relativePath = relativePath
        self.createdAt = createdAt
    }
}

struct TravelPlan: Codable, Equatable, Identifiable {
    var id: UUID
    var title: String
    var capability: PlanCapability
    var destinations: [PlanDestination]
    var days: [PlanDay]
    var sections: [PlanSection]
    var dateRange: String?
    var travelers: String?
    var style: String?
    var baseArea: String?
    var budget: String?
    var accommodationBudget: String?
    var assumptions: [String]
    var places: [PlanPlace]
    var photos: [PlanPhoto]
    var coverRelativePath: String?
    var originalHTMLRelativePath: String?
    var sourceName: String
    var sourceHash: String?
    var sourcePayloadJSON: String?
    var importedAt: Date
    var updatedAt: Date
    var isArchived: Bool

    init(
        id: UUID = UUID(),
        title: String,
        capability: PlanCapability = .viewOnly,
        destinations: [PlanDestination] = [],
        days: [PlanDay] = [],
        sections: [PlanSection] = [],
        dateRange: String? = nil,
        travelers: String? = nil,
        style: String? = nil,
        baseArea: String? = nil,
        budget: String? = nil,
        accommodationBudget: String? = nil,
        assumptions: [String] = [],
        places: [PlanPlace] = [],
        photos: [PlanPhoto] = [],
        coverRelativePath: String? = nil,
        originalHTMLRelativePath: String? = nil,
        sourceName: String,
        sourceHash: String? = nil,
        sourcePayloadJSON: String? = nil,
        importedAt: Date = Date(),
        updatedAt: Date = Date(),
        isArchived: Bool = false
    ) {
        self.id = id
        self.title = title
        self.capability = capability
        self.destinations = destinations
        self.days = days
        self.sections = sections
        self.dateRange = dateRange
        self.travelers = travelers
        self.style = style
        self.baseArea = baseArea
        self.budget = budget
        self.accommodationBudget = accommodationBudget
        self.assumptions = assumptions
        self.places = places
        self.photos = photos
        self.coverRelativePath = coverRelativePath
        self.originalHTMLRelativePath = originalHTMLRelativePath
        self.sourceName = sourceName
        self.sourceHash = sourceHash
        self.sourcePayloadJSON = sourcePayloadJSON
        self.importedAt = importedAt
        self.updatedAt = updatedAt
        self.isArchived = isArchived
    }
}

struct PlanSnapshot: Codable, Equatable {
    var schemaVersion: Int = AppMetadata.storeSchema
    var plans: [TravelPlan]
}

struct StorageSummary: Equatable {
    var planCount: Int
    var photoCount: Int
    var privateBytes: Int64
}

struct GalleryDeleteRequest {
    var photoIDsByPlan: [UUID: Set<UUID>]
    var coverPlanIDs: Set<UUID>

    init(photoIDsByPlan: [UUID: Set<UUID>] = [:], coverPlanIDs: Set<UUID> = []) {
        self.photoIDsByPlan = photoIDsByPlan
        self.coverPlanIDs = coverPlanIDs
    }
}
