import SwiftUI

@main
struct LujianApp: App {
    @StateObject private var store: PlanStore
    @State private var didSeedDemo = false
    private let shouldSeedDemo: Bool

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        shouldSeedDemo = arguments.contains("-seed-demo")
        if arguments.contains("-ui-testing") {
            let root = FileManager.default.temporaryDirectory
                .appendingPathComponent("LujianUITest-\(UUID().uuidString)", isDirectory: true)
            _store = StateObject(wrappedValue: PlanStore.temporary(root: root))
        } else {
            _store = StateObject(wrappedValue: PlanStore.live())
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(store: store)
                .task {
                    guard shouldSeedDemo, !didSeedDemo else { return }
                    didSeedDemo = true
                    try? DemoPlanLoader.importIfNeeded(store: store)
                }
        }
    }
}
