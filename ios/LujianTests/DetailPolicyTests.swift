import XCTest
@testable import Lujian

final class DetailPolicyTests: XCTestCase {
    func testDailyRouteKeepsOnlyCoordinateStopsInItemOrder() {
        let day = PlanDay(
            id: "day-1",
            label: "第一天",
            title: "路线",
            mapStops: [
                PlanMapStop(id: "a", title: "A", latitude: 38, longitude: 121),
                PlanMapStop(id: "b", title: "B"),
                PlanMapStop(id: "c", title: "C", latitude: 39, longitude: 122)
            ]
        )
        XCTAssertEqual(DailyRoutePolicy.stops(for: day).map(\.title), ["A", "C"])
    }

    func testSelectedDayFallsBackAfterEdit() {
        let days = [PlanDay(id: "day-2", label: "第二天", title: "返程")]
        XCTAssertEqual(DetailSelectionPolicy.validDayID("removed", in: days), "day-2")
        XCTAssertEqual(DetailSelectionPolicy.validDayID("day-2", in: days), "day-2")
        XCTAssertNil(DetailSelectionPolicy.validDayID(nil, in: []))
    }
}
