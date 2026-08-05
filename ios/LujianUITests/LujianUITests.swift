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
        let plan = app.buttons["计划卡片-大连五日旅行计划"]
        XCTAssertTrue(plan.waitForExistence(timeout: 10))
        plan.tap()

        let sections = app.segmentedControls["计划详情分区"]
        XCTAssertTrue(sections.waitForExistence(timeout: 8))
        sections.buttons["地图"].tap()
        XCTAssertTrue(app.otherElements["每日路线地图"].waitForExistence(timeout: 8))

        sections.buttons["预算"].tap()
        XCTAssertTrue(app.staticTexts["预计 3000 元"].waitForExistence(timeout: 5))
        sections.buttons["行程"].tap()
        XCTAssertTrue(app.staticTexts["星海广场"].waitForExistence(timeout: 5))
    }

    func testArchiveRestoreAndGalleryManagement() {
        app.tabBars.buttons["旅笺板"].tap()
        XCTAssertTrue(app.buttons["计划卡片-大连五日旅行计划"].waitForExistence(timeout: 10))
        app.buttons["管理计划"].tap()
        app.buttons["选择计划-大连五日旅行计划"].tap()
        app.buttons["归档"].tap()
        app.buttons["管理计划"].tap()

        app.buttons["旅笺板切换"].tap()
        app.buttons["足迹板"].tap()
        XCTAssertTrue(app.buttons["计划卡片-大连五日旅行计划"].waitForExistence(timeout: 5))
        app.buttons["管理计划"].tap()
        app.buttons["选择计划-大连五日旅行计划"].tap()
        app.buttons["恢复"].tap()

        app.tabBars.buttons["相册"].tap()
        XCTAssertTrue(app.otherElements["空相册提示"].waitForExistence(timeout: 5))
        app.buttons["管理相册"].tap()
        XCTAssertEqual(app.buttons["管理相册"].label, "完成")
        app.buttons["管理相册"].tap()
    }
}
