import Foundation

enum WebDecision: Equatable {
    case allow
    case cancel
    case openExternally
}

enum WebNavigationPolicy {
    static func decision(for url: URL, isMainFrame: Bool) -> WebDecision {
        if url.isFileURL { return .allow }
        guard let scheme = url.scheme?.lowercased() else { return .cancel }
        if scheme == "about" { return .allow }
        if scheme == "https" { return isMainFrame ? .openExternally : .allow }
        return .cancel
    }
}
