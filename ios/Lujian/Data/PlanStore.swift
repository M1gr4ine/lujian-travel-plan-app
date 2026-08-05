import Foundation
import SwiftUI

enum PlanStoreError: LocalizedError, Equatable {
    case planNotFound
    case pathOutsidePrivateStore
    case unsupportedImage

    var errorDescription: String? {
        switch self {
        case .planNotFound: "计划不存在"
        case .pathOutsidePrivateStore: "文件路径超出应用私有目录"
        case .unsupportedImage: "仅支持 JPEG、PNG 或 HEIC 图片"
        }
    }
}

@MainActor
final class PlanStore: ObservableObject {
    @Published private(set) var plans: [TravelPlan]
    let recoveryIssue: String?

    private let root: URL
    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    private var indexURL: URL { root.appendingPathComponent("store.json") }
    private var plansRoot: URL { root.appendingPathComponent("plans", isDirectory: true) }

    private init(root: URL, fileManager: FileManager = .default) {
        self.root = root.standardizedFileURL
        self.fileManager = fileManager
        self.encoder = Self.makeEncoder()
        self.decoder = Self.makeDecoder()

        var loadedPlans: [TravelPlan] = []
        var issue: String?
        do {
            try fileManager.createDirectory(at: self.root, withIntermediateDirectories: true)
            try fileManager.createDirectory(
                at: self.root.appendingPathComponent("plans", isDirectory: true),
                withIntermediateDirectories: true
            )
            let index = self.root.appendingPathComponent("store.json")
            if fileManager.fileExists(atPath: index.path) {
                let snapshot = try decoder.decode(PlanSnapshot.self, from: Data(contentsOf: index))
                guard snapshot.schemaVersion == AppMetadata.storeSchema else {
                    throw CocoaError(.coderReadCorrupt)
                }
                loadedPlans = snapshot.plans
            }
        } catch {
            let index = self.root.appendingPathComponent("store.json")
            let corrupt = self.root.appendingPathComponent("store.corrupt.json")
            if fileManager.fileExists(atPath: index.path) {
                try? fileManager.removeItem(at: corrupt)
                try? fileManager.copyItem(at: index, to: corrupt)
            }
            issue = "索引已隔离，原文件仍保留：\(error.localizedDescription)"
        }
        self.plans = loadedPlans
        self.recoveryIssue = issue
    }

    static func live() -> PlanStore {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return PlanStore(root: base.appendingPathComponent("Lujian", isDirectory: true))
    }

    static func temporary(root: URL) -> PlanStore {
        PlanStore(root: root)
    }

    var storageSummary: StorageSummary {
        let photoCount = plans.reduce(0) { $0 + $1.photos.count }
        let keys: Set<URLResourceKey> = [.isRegularFileKey, .fileSizeKey]
        let bytes = fileManager.enumerator(
            at: root,
            includingPropertiesForKeys: Array(keys),
            options: [.skipsHiddenFiles]
        )?.compactMap { $0 as? URL }.reduce(Int64(0)) { partial, url in
            let values = try? url.resourceValues(forKeys: keys)
            guard values?.isRegularFile == true else { return partial }
            return partial + Int64(values?.fileSize ?? 0)
        } ?? 0
        return StorageSummary(planCount: plans.count, photoCount: photoCount, privateBytes: bytes)
    }

    func plan(id: UUID) -> TravelPlan? {
        plans.first { $0.id == id }
    }

    func upsert(_ plan: TravelPlan) throws {
        let previous = plans
        var updated = plan
        updated.updatedAt = Date()
        if let index = plans.firstIndex(where: { $0.id == updated.id }) {
            plans[index] = updated
        } else {
            plans.append(updated)
        }
        do {
            try persistAtomically()
        } catch {
            plans = previous
            throw error
        }
    }

    func archive(ids: Set<UUID>) throws {
        try setArchived(ids: ids, value: true)
    }

    func restore(ids: Set<UUID>) throws {
        try setArchived(ids: ids, value: false)
    }

    func delete(ids: Set<UUID>) throws {
        let previous = plans
        plans.removeAll { ids.contains($0.id) }
        do {
            try persistAtomically()
        } catch {
            plans = previous
            throw error
        }
        for id in ids {
            try? fileManager.removeItem(at: plansRoot.appendingPathComponent(id.uuidString, isDirectory: true))
        }
    }

    @discardableResult
    func savePhoto(data: Data, planID: UUID, placeID: String?) throws -> PlanPhoto {
        guard var plan = plan(id: planID) else { throw PlanStoreError.planNotFound }
        let fileExtension = try imageFileExtension(data)
        let photoID = UUID()
        let relativePath = "plans/\(planID.uuidString)/media/\(photoID.uuidString).\(fileExtension)"
        let url = try writePrivateFile(data, relativePath: relativePath)
        let photo = PlanPhoto(id: photoID, placeID: placeID, relativePath: relativePath)
        plan.photos.append(photo)
        do {
            try upsert(plan)
            return photo
        } catch {
            try? fileManager.removeItem(at: url)
            throw error
        }
    }

    @discardableResult
    func saveCover(data: Data, planID: UUID) throws -> String {
        guard var plan = plan(id: planID) else { throw PlanStoreError.planNotFound }
        let fileExtension = try imageFileExtension(data)
        let relativePath = "plans/\(planID.uuidString)/cover-\(UUID().uuidString).\(fileExtension)"
        let oldPath = plan.coverRelativePath
        let url = try writePrivateFile(data, relativePath: relativePath)
        plan.coverRelativePath = relativePath
        do {
            try upsert(plan)
            if let oldPath, oldPath != relativePath {
                try? deletePrivateFile(relativePath: oldPath)
            }
            return relativePath
        } catch {
            try? fileManager.removeItem(at: url)
            throw error
        }
    }

    func deleteGalleryItems(_ request: GalleryDeleteRequest) throws {
        let previous = plans
        var paths: [String] = []
        for index in plans.indices {
            let planID = plans[index].id
            let photoIDs = request.photoIDsByPlan[planID] ?? []
            paths.append(contentsOf: plans[index].photos.filter { photoIDs.contains($0.id) }.map(\.relativePath))
            plans[index].photos.removeAll { photoIDs.contains($0.id) }
            if request.coverPlanIDs.contains(planID), let cover = plans[index].coverRelativePath {
                paths.append(cover)
                plans[index].coverRelativePath = nil
            }
        }
        do {
            try persistAtomically()
        } catch {
            plans = previous
            throw error
        }
        for path in paths { try? deletePrivateFile(relativePath: path) }
    }

    @discardableResult
    func writeOriginalHTML(_ data: Data, planID: UUID) throws -> String {
        let relativePath = "plans/\(planID.uuidString)/original-\(UUID().uuidString).html"
        _ = try writePrivateFile(data, relativePath: relativePath)
        return relativePath
    }

    func privateFileURL(relativePath: String) throws -> URL {
        let base = plansRoot.standardizedFileURL
        let candidate = root.appendingPathComponent(relativePath).standardizedFileURL
        let prefix = base.path.hasSuffix("/") ? base.path : base.path + "/"
        guard candidate.path == base.path || candidate.path.hasPrefix(prefix) else {
            throw PlanStoreError.pathOutsidePrivateStore
        }
        return candidate
    }

    func deletePrivateFile(relativePath: String) throws {
        let url = try privateFileURL(relativePath: relativePath)
        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
    }

    private func writePrivateFile(_ data: Data, relativePath: String) throws -> URL {
        let url = try privateFileURL(relativePath: relativePath)
        try fileManager.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
        return url
    }

    private func setArchived(ids: Set<UUID>, value: Bool) throws {
        let previous = plans
        for index in plans.indices where ids.contains(plans[index].id) {
            plans[index].isArchived = value
            plans[index].updatedAt = Date()
        }
        do {
            try persistAtomically()
        } catch {
            plans = previous
            throw error
        }
    }

    private func persistAtomically() throws {
        let snapshot = PlanSnapshot(plans: plans)
        try encoder.encode(snapshot).write(to: indexURL, options: .atomic)
    }

    private func imageFileExtension(_ data: Data) throws -> String {
        let bytes = [UInt8](data.prefix(12))
        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return "jpg" }
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) { return "png" }
        if bytes.count >= 12,
           String(bytes: bytes[4..<8], encoding: .ascii) == "ftyp",
           let brand = String(bytes: bytes[8..<12], encoding: .ascii),
           ["heic", "heix", "hevc", "mif1"].contains(brand) {
            return "heic"
        }
        throw PlanStoreError.unsupportedImage
    }

    private static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
