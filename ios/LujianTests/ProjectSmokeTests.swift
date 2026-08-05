import XCTest
@testable import Lujian

final class ProjectSmokeTests: XCTestCase {
    func testVersionContractDrivesUserVisibleMetadata() {
        XCTAssertEqual(AppMetadata.version, "1.0.0")
        XCTAssertEqual(AppMetadata.build, "1")
        XCTAssertEqual(AppMetadata.storeSchema, 1)
    }
}

