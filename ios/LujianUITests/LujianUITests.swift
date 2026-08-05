import XCTest

final class LujianUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-seed-demo"]
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["旅笺板"].waitForExistence(timeout: 12))
    }

    func testCoreJourney() {
        app.tabBars.buttons["旅笺板"].tap()
        let plan = element("计划卡片-大连五日旅行计划")
        XCTAssertTrue(plan.waitForExistence(timeout: 10))
        plan.tap()

        let sections = element("计划详情分区")
        XCTAssertTrue(sections.waitForExistence(timeout: 8))
        app.buttons["地图"].tap()
        XCTAssertTrue(element("每日路线地图").waitForExistence(timeout: 8))

        app.buttons["预算"].tap()
        XCTAssertTrue(app.staticTexts["预计 3000 元"].waitForExistence(timeout: 5))
        app.buttons["行程"].tap()
        XCTAssertTrue(app.staticTexts["星海广场"].waitForExistence(timeout: 5))
    }

    func testArchiveRestoreAndGalleryManagement() {
        app.tabBars.buttons["旅笺板"].tap()
        XCTAssertTrue(element("计划卡片-大连五日旅行计划").waitForExistence(timeout: 10))
        element("管理计划").tap()
        element("选择计划-大连五日旅行计划").tap()
        app.buttons["归档"].tap()
        element("管理计划").tap()

        element("旅笺板切换").tap()
        app.buttons["足迹板"].tap()
        XCTAssertTrue(element("计划卡片-大连五日旅行计划").waitForExistence(timeout: 5))
        element("管理计划").tap()
        element("选择计划-大连五日旅行计划").tap()
        app.buttons["恢复"].tap()

        app.tabBars.buttons["相册"].tap()
        XCTAssertTrue(app.staticTexts["相册还是空的"].waitForExistence(timeout: 5))
        element("管理相册").tap()
        XCTAssertEqual(element("管理相册").label, "完成")
        element("管理相册").tap()
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier]
    }
}
