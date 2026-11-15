import SwiftUI
import WebKit

struct WebView: UIViewRepresentable {
    let url: URL

    final class Coordinator {
        /// The URL the web view was last asked to load; SwiftUI calls `updateUIView` far more often.
        var loadedURL: URL?
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
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // Reloading on every SwiftUI update would restart playback; load only when the URL changes.
        guard context.coordinator.loadedURL != url else { return }
        context.coordinator.loadedURL = url
        uiView.load(URLRequest(url: url))
    }
}
