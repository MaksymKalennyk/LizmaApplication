import SwiftUI

/// A post's track: the 30-second preview right here, and the whole song in Apple Music or Spotify.
struct TrackPlayerSheet: View {
    let post: PostResponse

    @ObservedObject private var player = PreviewPlayer.shared
    @Environment(\.openURL) private var openURL

    private var previewURL: URL? { post.previewUrl.flatMap(URL.init(string:)) }
    private var coverURL: URL? { post.albumCoverUrl.flatMap(URL.init(string:)) }

    var body: some View {
        VStack(spacing: 18) {
            HStack(spacing: 14) {
                cover
                VStack(alignment: .leading, spacing: 3) {
                    Text(post.trackName)
                        .font(.headline)
                        .lineLimit(2)
                    Text(post.artistName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }

            if let previewURL {
                preview(previewURL)
            } else if post.musicProvider == "SPOTIFY" {
                // Older posts from Spotify without an Apple preview: Spotify's own player.
                SpotifyEmbedView(trackId: post.musicId.replacingOccurrences(of: "spotify:track:", with: ""))
                    .frame(height: 80)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            VStack(spacing: 10) {
                Button {
                    openURL(appleMusicURL)
                } label: {
                    Label("Open in Apple Music", systemImage: "applelogo")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)

                Button {
                    openURL(spotifyURL)
                } label: {
                    Label("Open in Spotify", systemImage: "music.note")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
            }
        }
        .padding(20)
        .onDisappear { player.stop() }
    }

    private var cover: some View {
        Group {
            if let coverURL {
                RemoteImage(url: coverURL) { image in
                    image.resizable().scaledToFill()
                } placeholder: { _ in
                    Color.white.opacity(0.1)
                }
            } else {
                ZStack {
                    Color.white.opacity(0.1)
                    Image(systemName: "music.note").foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: 64, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityHidden(true)
    }

    private func preview(_ url: URL) -> some View {
        let isThis = player.isCurrent(url)
        return HStack(spacing: 14) {
            Button {
                player.toggle(url)
            } label: {
                Image(systemName: isThis && player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.title2)
                    .frame(width: 52, height: 52)
                    .background(.tint, in: Circle())
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isThis && player.isPlaying ? "Pause preview" : "Play preview")

            VStack(alignment: .leading, spacing: 6) {
                ProgressView(value: isThis ? player.progress : 0)
                Text("30-second preview")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// The track's page, or a search for it when its link isn't known (older posts from Spotify).
    private var appleMusicURL: URL {
        if let link = post.appleMusicUrl.flatMap(URL.init(string:)) { return link }
        if post.musicProvider == "APPLE", post.musicId.allSatisfy(\.isNumber) {
            return URL(string: "https://music.apple.com/song/\(post.musicId)")!
        }
        var search = URLComponents(string: "https://music.apple.com/search")!
        search.queryItems = [URLQueryItem(name: "term", value: searchText)] // encodes a "&" in the title too
        return search.url!
    }

    /// The track in Spotify when the server knows it, otherwise Spotify's search for it (the service that
    /// found the exact track, song.link, no longer answers without a key).
    private var spotifyURL: URL {
        if let link = post.spotifyUrl.flatMap(URL.init(string:)) { return link }
        let query = searchText.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed) ?? ""
        return URL(string: "https://open.spotify.com/search/" + query)!
    }

    private var searchText: String { "\(post.trackName) \(post.artistName)" }
}
