import Foundation
import XCTest
@testable import Lujian

final class BoardPolicyTests: XCTestCase {
    func testSelectionRetainsVisibleIDsAndSelectAllUsesCurrentBoard() {
        let first = TestFixtures.plan(
            title: "A",
            id: UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
        )
        let second = TestFixtures.plan(
            title: "B",
            id: UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
        )
        let archived = TestFixtures.plan(
            title: "C",
            archived: true,
            id: UUID(uuidString: "33333333-3333-3333-3333-333333333333")!
        )

        XCTAssertEqual(
            BoardSelectionPolicy.selectAll(plans: [first, second, archived], mode: .active),
            [first.id, second.id]
        )
        XCTAssertEqual(
            BoardSelectionPolicy.retain([first.id, archived.id], in: [first, second]),
            [first.id]
        )
    }
}
