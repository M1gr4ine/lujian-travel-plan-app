import Foundation
import XCTest
@testable import Lujian

final class ExportAndSecurityTests: XCTestCase {
    func testExportRoundTripsCoreContract() throws {
        let source = TestFixtures.plan(title: "大连五日")
        let data = try PlanHTMLExporter.data(for: source)

        XCTAssertFalse(data.starts(with: [0xEF, 0xBB, 0xBF]))
        let decoded = try HTMLDecoder.decode(data: data, fileName: "export.html")
        let parsed = try PlanHTMLParser.parse(decoded)
        XCTAssertEqual(parsed.plan.title, source.title)
        XCTAssertEqual(parsed.plan.days, source.days)
    }

    func testNavigationPolicyRejectsUnsafeSchemes() throws {
        XCTAssertEqual(
            WebNavigationPolicy.decision(
                for: try XCTUnwrap(URL(string: "http://example.com")),
                isMainFrame: true
            ),
            .cancel
        )
        XCTAssertEqual(
            WebNavigationPolicy.decision(
                for: try XCTUnwrap(URL(string: "javascript:alert(1)")),
                isMainFrame: true
            ),
            .cancel
        )
        XCTAssertEqual(
            WebNavigationPolicy.decision(
                for: try XCTUnwrap(URL(string: "https://example.com")),
                isMainFrame: true
            ),
            .openExternally
        )
        XCTAssertEqual(
            WebNavigationPolicy.decision(
                for: URL(fileURLWithPath: "/tmp/plan.html"),
                isMainFrame: true
            ),
            .allow
        )
    }
}
