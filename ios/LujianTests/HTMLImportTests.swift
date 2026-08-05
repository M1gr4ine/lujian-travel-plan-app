import Foundation
import XCTest
@testable import Lujian

final class HTMLImportTests: XCTestCase {
    func testEnhancedPlanDecodesAndParses() throws {
        let decoded = try HTMLDecoder.decode(
            data: Data(TestFixtures.html(title: "大连").utf8),
            fileName: "dalian.html"
        )
        let parsed = try PlanHTMLParser.parse(decoded)

        XCTAssertEqual(parsed.plan.title, "大连")
        XCTAssertEqual(parsed.plan.days.first?.items.first?.title, "星海广场")
        XCTAssertEqual(decoded.encodingName, "UTF-8")
    }

    func testRejectsInvalidContractAndOversizedInput() throws {
        XCTAssertThrowsError(
            try HTMLDecoder.decode(data: Data("not html".utf8), fileName: "fake.html")
        )
        let duplicate = TestFixtures.html() + TestFixtures.html()
        let duplicateDecoded = try HTMLDecoder.decode(data: Data(duplicate.utf8), fileName: "duplicate.html")
        XCTAssertThrowsError(try PlanHTMLParser.parse(duplicateDecoded))

        let incompatible = try HTMLDecoder.decode(
            data: Data(TestFixtures.html(schemaVersion: 2).utf8),
            fileName: "future.html"
        )
        XCTAssertThrowsError(try PlanHTMLParser.parse(incompatible))

        let oversized = Data(count: HTMLDecoder.maximumBytes + 1)
        XCTAssertThrowsError(try HTMLDecoder.decode(data: oversized, fileName: "large.html"))
    }

    func testUTF8BOMAndGB18030Decode() throws {
        let utf8 = Data([0xEF, 0xBB, 0xBF]) + Data(TestFixtures.html(title: "带签名").utf8)
        XCTAssertEqual(
            try HTMLDecoder.decode(data: utf8, fileName: "bom.htm").encodingName,
            "UTF-8 BOM"
        )

        let source = "<html><head><meta charset=\"gb18030\"><title>青岛慢游</title></head><body>计划</body></html>"
        let gb18030 = try XCTUnwrap(source.data(using: HTMLDecoder.gb18030Encoding))
        let decoded = try HTMLDecoder.decode(data: gb18030, fileName: "qingdao.html")
        XCTAssertEqual(decoded.encodingName, "GB18030")
        XCTAssertEqual(try PlanHTMLParser.parse(decoded).plan.title, "青岛慢游")
    }

    func testOrdinaryHTMLReadsExplicitGeographyMetadata() throws {
        let html = """
        <html><head><title>杭州周末</title>
        <meta name="travel:destination" content="杭州">
        <meta name="lujian:country-code" content="CN">
        <meta name="lujian:latitude" content="30.2741">
        <meta name="lujian:longitude" content="120.1551">
        </head><body>周末计划</body></html>
        """
        let decoded = try HTMLDecoder.decode(data: Data(html.utf8), fileName: "hangzhou.html")
        let destination = try XCTUnwrap(PlanHTMLParser.parse(decoded).plan.destinations.first)
        XCTAssertEqual(destination.name, "杭州")
        XCTAssertEqual(destination.countryCode, "CN")
        XCTAssertEqual(destination.latitude, 30.2741)
        XCTAssertEqual(destination.longitude, 120.1551)
    }

    @MainActor
    func testSameHashUpdatesWithoutLosingArchiveState() throws {
        let root = try TestFixtures.temporaryRoot()
        let store = PlanStore.temporary(root: root)
        let service = PlanImportService(store: store)
        let data = Data(TestFixtures.html(title: "A").utf8)
        let first = try service.importData(data, fileName: "a.html")
        try store.archive(ids: [first.planID])

        let second = try service.importData(data, fileName: "copy.html")

        XCTAssertEqual(first.planID, second.planID)
        XCTAssertTrue(try XCTUnwrap(store.plan(id: second.planID)).isArchived)
        XCTAssertTrue(second.replacedExisting)
        let planRoot = root.appendingPathComponent("plans/\(second.planID.uuidString)")
        let htmlFiles = try FileManager.default.contentsOfDirectory(at: planRoot, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "html" }
        XCTAssertEqual(htmlFiles.count, 1)
    }
}
