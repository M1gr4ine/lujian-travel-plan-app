import SwiftUI

@main
struct LujianApp: App {
    @StateObject private var store: PlanStore

    init() {
        _store = StateObject(wrappedValue: PlanStore.live())
    }

    var body: some Scene {
        WindowGroup {
            RootView(store: store)
        }
    }
}
