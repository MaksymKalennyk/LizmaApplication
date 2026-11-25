import Foundation

/// The share extension's key: it posts with it instead of the user's session (renewing the session from an extension
/// would race the app and sign it out). The app gets it after signing in; it lives in the App Group's Keychain, so
/// the extension can read it and it never leaves this phone.
enum ShareKey {
    private static let account = "lizma.shareKey"

    static var current: String? {
        get { try? Keychain.get(account, accessGroup: WidgetShared.appGroup) }
        set {
            if let newValue {
                try? Keychain.set(newValue, forKey: account, accessGroup: WidgetShared.appGroup)
            } else {
                Keychain.delete(account, accessGroup: WidgetShared.appGroup)
            }
        }
    }
}

/// A link shared from Apple Music or Spotify (or a text with one in it), with the phone's region: its store is
/// searched when the link doesn't name one.
struct ShareLinkRequest: Encodable {
    let url: String
    let country: String

    init(url: String) {
        self.url = url
        self.country = Locale.current.region?.identifier ?? "US"
    }
}

/// A song the server found from a shared link: Apple's catalog entry, and the exact Spotify track when the link came
/// from Spotify.
struct SharedTrack: Codable, Identifiable, Hashable {
    let musicId: String
    let trackName: String
    let artistName: String
    let albumName: String?
    let albumCoverUrl: String?
    let previewUrl: String?
    let appleMusicUrl: String?
    let spotifyUrl: String?

    var id: String { musicId }
}

/// `matched`: `tracks` holds the song. Otherwise they're the likeliest songs, for the user to choose from.
struct ShareResolution: Decodable {
    let matched: Bool
    let tracks: [SharedTrack]
}

enum SharedLinks {
    private static let markers = ["music.apple.com/", "itunes.apple.com/", "open.spotify.com/", "spotify.link/",
                                  "spoti.fi/", "apple.co/", "spotify:track:"]

    /// Whether a text holds a link from Apple Music or Spotify: the composer then looks the song up instead of
    /// searching for the words.
    static func containsMusicLink(_ text: String) -> Bool {
        let lowered = text.lowercased()
        return markers.contains { lowered.contains($0) }
    }
}
