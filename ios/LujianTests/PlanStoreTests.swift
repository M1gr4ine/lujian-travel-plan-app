import Foundation
import XCTest
@testable import Lujian

@MainActor
final class PlanStoreTests: XCTestCase {
    func testStorePersistsAndRejectsOutsideDeletion() throws {
        let root = try TestFixtures.temporaryRoot()
        let store = PlanStore.temporary(root: root)
        try store.upsert(TestFixtures.plan(title: "大连五日"))

        let reloaded = PlanStore.temporary(root: root)
        XCTAssertEqual(reloaded.plans.map(\.title), ["大连五日"])
        XCTAssertThrowsError(try reloaded.deletePrivateFile(relativePath: "../outside.jpg"))
    }

    func testArchiveRestoreAndBatchDeleteAreStable() throws {
        let store = PlanStore.temporary(root: try TestFixtures.temporaryRoot())
        let first = TestFixtures.plan(title: "A")
        let second = TestFixtures.plan(
            title: "B",
            id: UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
        )
        try store.upsert(first)
        try store.upsert(second)

        try store.archive(ids: [first.id, second.id])
        XCTAssertTrue(store.plans.allSatisfy(\.isArchived))
        try store.restore(ids: [first.id])
        XCTAssertFalse(try XCTUnwrap(store.plan(id: first.id)).isArchived)
        try store.delete(ids: [second.id])
        XCTAssertNil(store.plan(id: second.id))
    }

    func testCorruptIndexIsPreservedAndReported() throws {
        let root = try TestFixtures.temporaryRoot()
        try Data("{broken".utf8).write(to: root.appendingPathComponent("store.json"))

        let store = PlanStore.temporary(root: root)

        XCTAssertTrue(store.plans.isEmpty)
        XCTAssertNotNil(store.recoveryIssue)
        XCTAssertTrue(
            FileManager.default.fileExists(
                atPath: root.appendingPathComponent("store.corrupt.json").path
            )
        )
    }
}
