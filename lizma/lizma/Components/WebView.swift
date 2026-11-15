import SwiftUI
import WebKit

struct WebView: UIViewRepresentable {
    let url: URL
    /// False for fixed-size embeds that must not scroll or bounce inside their frame.
    var isScrollEnabled = true
    /// Called when the page finished loading (`true`) or couldn't be loaded (`false`).
    var onLoad: (Bool) -> Void = { _ in }

    final class Coordinator: NSObject, WKNavigationDelegate {
        /// The URL the web view was last asked to load; SwiftUI calls `updateUIView` far more often.
        var loadedURL: URL?
        var onLoad: (Bool) -> Void = { _ in }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            onLoad(true)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            onLoad(false)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            onLoad(false)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences = prefs

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = isScrollEnabled
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.onLoad = onLoad
        // Reloading on every SwiftUI update would restart playback; load only when the URL changes.
        guard context.coordinator.loadedURL != url else { return }
        context.coordinator.loadedURL = url
        uiView.load(URLRequest(url: url))
    }
}
