import SwiftUI

enum LujianPalette {
    static let paper = Color(red: 0.980, green: 0.965, blue: 0.937)
    static let paperDeep = Color(red: 0.941, green: 0.914, blue: 0.867)
    static let ink = Color(red: 0.165, green: 0.145, blue: 0.125)
    static let gold = Color(red: 0.949, green: 0.706, blue: 0.227)
    static let coral = Color(red: 0.722, green: 0.373, blue: 0.322)
}

struct PaperCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(20)
            .background(LujianPalette.paper)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(LujianPalette.ink.opacity(0.12), lineWidth: 1)
            }
            .shadow(color: LujianPalette.ink.opacity(0.10), radius: 18, y: 8)
    }
}

extension View {
    func paperCard() -> some View {
        modifier(PaperCardModifier())
    }
}

