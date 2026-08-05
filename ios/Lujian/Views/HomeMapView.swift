import MapKit
import SwiftUI

private struct HomePlanPin: Identifiable {
    let id: UUID
    let title: String
    let coordinate: CLLocationCoordinate2D
}

struct HomeMapView: View {
    @ObservedObject var store: PlanStore
    @State private var position: MapCameraPosition = .automatic

    private var activePlans: [TravelPlan] { store.plans.filter { !$0.isArchived } }
    private var pins: [HomePlanPin] {
        activePlans.compactMap { plan in
            guard let destination = plan.destinations.first,
                  let latitude = destination.latitude,
                  let longitude = destination.longitude else { return nil }
            return HomePlanPin(
                id: plan.id,
                title: plan.title,
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
            )
        }
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                Map(position: $position) {
                    ForEach(pins) { pin in
                        Marker(pin.title, systemImage: "mappin", coordinate: pin.coordinate)
                            .tint(LujianPalette.coral)
                    }
                }
                .mapStyle(.standard(elevation: .flat))
                .mapControls {
                    MapCompass()
                    MapScaleView()
                }
                .accessibilityIdentifier("首页计划地图")

                VStack(alignment: .leading, spacing: 4) {
                    Text("旅笺")
                        .font(.largeTitle.bold())
                    Text(pins.isEmpty ? "导入带坐标的计划，就会在这里落针" : "\(pins.count) 段旅程正在地图上等你")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(16)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .padding()
            }
            .safeAreaInset(edge: .bottom) {
                if !activePlans.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(activePlans) { plan in
                                NavigationLink {
                                    PlanDetailView(planID: plan.id, store: store)
                                } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(plan.title).font(.headline).lineLimit(1)
                                        Text(plan.dateRange ?? plan.destinations.first?.name ?? "旅行计划")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    .frame(width: 190, alignment: .leading)
                                    .paperCard()
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                    }
                    .background(.ultraThinMaterial)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}
