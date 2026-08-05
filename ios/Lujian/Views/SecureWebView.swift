import SwiftUI
import UIKit
import WebKit

struct SecureWebView: UIViewRepresentable {
    let fileURL: URL
    var allowsJavaScript = false

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = allowsJavaScript
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = UIColor(LujianPalette.paper)
        webView.scrollView.backgroundColor = UIColor(LujianPalette.paper)
        load(fileURL, in: webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard webView.url != fileURL else { return }
        load(fileURL, in: webView)
    }

    private func load(_ url: URL, in webView: WKWebView) {
        webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            switch WebNavigationPolicy.decision(
                for: url,
                isMainFrame: navigationAction.targetFrame?.isMainFrame ?? true
            ) {
            case .allow:
                decisionHandler(.allow)
            case .cancel:
                decisionHandler(.cancel)
            case .openExternally:
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            }
        }
    }
}
