import SwiftUI

/// Spotify's compact track player (plays a preview for everyone) in a small sheet over the feed.
struct SpotifyEmbedView: View {
    let trackId: String

    /// Height of Spotify's compact embed.
    static let playerHeight: CGFloat = 152
    /// The sheet is a little taller, so the player sits clear of the grabber.
    static let sheetHeight: CGFloat = 200

    private enum LoadState {
        case loading, loaded, failed
    }

    @State private var loadState = LoadState.loading

    private var embedURL: URL? {
        guard let id = trackId.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed), !id.isEmpty else {
            return nil
        }
        return URL(string: "https://open.spotify.com/embed/track/\(id)")
    }

    var body: some View {
        ZStack {
            // Shown until the page is there, so the sheet never opens as an empty black box.
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.white.opacity(0.08))

            if let embedURL, loadState != .failed {
                WebView(url: embedURL, isScrollEnabled: false) { finished in
                    loadState = finished ? .loaded : .failed
                }
                .opacity(loadState == .loaded ? 1 : 0)

                if loadState == .loading {
                    ProgressView().tint(.white)
                }
            } else {
                Label("Couldn't load the player", systemImage: "wifi.exclamationmark")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(height: Self.playerHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 12)
        // Centred in the whole sheet (its bottom safe area included), so the margins above and below match.
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(edges: .bottom)
    }
}
