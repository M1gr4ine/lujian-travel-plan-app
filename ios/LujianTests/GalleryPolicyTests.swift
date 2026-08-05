import Foundation
import XCTest
@testable import Lujian

final class GalleryPolicyTests: XCTestCase {
    func testGallerySelectAllDeduplicatesAcrossGroups() {
        let photo = GallerySelectionKey.photo(planID: UUID(), photoID: UUID())
        XCTAssertEqual(GallerySelectionPolicy.selectAll([photo, photo]), [photo])
    }

    func testSummarySeparatesPhotosAndCovers() {
        let planID = UUID()
        let keys: Set<GallerySelectionKey> = [
            .photo(planID: planID, photoID: UUID()),
            .cover(planID: planID)
        ]
        XCTAssertEqual(GallerySelectionPolicy.summary(keys), "删除 1 张照片和 1 张自定义预览图？")
    }

    func testRefreshRetainsOnlyAvailableStableKeys() {
        let planID = UUID()
        let retained = GallerySelectionKey.cover(planID: planID)
        let removed = GallerySelectionKey.photo(planID: planID, photoID: UUID())
        XCTAssertEqual(
            GallerySelectionPolicy.retain([retained, removed], available: [retained]),
            [retained]
        )
    }
}
