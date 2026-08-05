import CryptoKit
import Foundation

struct ImportOutcome: Equatable {
    let planID: UUID
    let replacedExisting: Bool
}

@MainActor
final class PlanImportService {
    private let store: PlanStore

    init(store: PlanStore) {
        self.store = store
    }

    func importURL(_ url: URL) async throws -> ImportOutcome {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            return try importData(Data(contentsOf: url), fileName: url.lastPathComponent)
        } catch let error as HTMLImportError {
            throw error
        } catch {
            throw HTMLImportError.unreadableFile
        }
    }

    func importData(_ data: Data, fileName: String) throws -> ImportOutcome {
        let decoded = try HTMLDecoder.decode(data: data, fileName: fileName)
        var plan = try PlanHTMLParser.parse(decoded).plan
        let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        let existing = store.plans.first { $0.sourceHash == hash }

        if let existing {
            plan.id = existing.id
            plan.photos = existing.photos
            plan.coverRelativePath = existing.coverRelativePath
            plan.originalHTMLRelativePath = existing.originalHTMLRelativePath
            plan.importedAt = existing.importedAt
            plan.isArchived = existing.isArchived
        }
        plan.sourceName = fileName
        plan.sourceHash = hash
        plan.originalHTMLRelativePath = try store.writeOriginalHTML(data, planID: plan.id)
        try store.upsert(plan)
        return ImportOutcome(planID: plan.id, replacedExisting: existing != nil)
    }

    func importBundledResource(name: String, `extension` fileExtension: String) throws -> ImportOutcome {
        guard let url = Bundle.main.url(forResource: name, withExtension: fileExtension) else {
            throw HTMLImportError.unreadableFile
        }
        return try importData(Data(contentsOf: url), fileName: url.lastPathComponent)
    }
}
