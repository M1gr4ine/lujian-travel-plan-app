import SwiftUI

struct RootView: View {
    @ObservedObject var store: PlanStore

    var body: some View {
        ZStack {
            LujianPalette.paperDeep
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "map.fill")
                    .font(.system(size: 42, weight: .semibold))
                    .foregroundStyle(LujianPalette.coral)
                    .accessibilityHidden(true)

                Text("旅笺")
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(LujianPalette.ink)

                Text("把旅行计划，收进随身手账")
                    .font(.body)
                    .foregroundStyle(LujianPalette.ink.opacity(0.70))
                    .multilineTextAlignment(.center)
            }
            .paperCard()
            .padding(24)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("旅笺，把旅行计划收进随身手账")
        }
    }
}

#Preview {
    RootView(store: .temporary(root: FileManager.default.temporaryDirectory.appendingPathComponent("LujianPreview")))
}
