import XCTest
@testable import Lujian

@MainActor
final class AppFlowTests: XCTestCase {
    func testDemoImportIsIdempotent() throws {
        let store = PlanStore.temporary(root: try TestFixtures.temporaryRoot())
        try DemoPlanLoader.importIfNeeded(store: store)
        try DemoPlanLoader.importIfNeeded(store: store)

        XCTAssertEqual(store.plans.filter { $0.title == "大连五日旅行计划" }.count, 1)
    }
}
