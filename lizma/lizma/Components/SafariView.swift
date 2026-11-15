import SwiftUI
import SafariServices

/// A web page (terms, privacy policy) shown in-app with Safari.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

/// Drives `.sheet(item:)` for a `SafariView`.
struct SafariLink: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
