import Foundation

/// The Lizma server, as the share extension talks to it: with the extension's own key (header X-Share-Key), in the
/// language chosen in the app.
struct ShareAPI {
    let baseURL: String
    let key: String

    enum Failure: Error {
        /// The app isn't signed in on this phone (or its sign-in ended): the key is gone or no longer works.
        case signedOut
        /// The server's own words, in the app's language ("Only songs can be shared").
        case message(String)
    }

    /// Nil until the app has been opened and signed in once: it hands over the server's address and the key.
    static func current() -> ShareAPI? {
        guard let key = ShareKey.current, let baseURL = WidgetShared.baseURL else { return nil }
        return ShareAPI(baseURL: baseURL, key: key)
    }

    /// "en" or "uk": the language chosen in the app, else the phone's.
    static var language: String {
        WidgetShared.language
            ?? Bundle.preferredLocalizations(from: ["en", "uk"], forPreferences: Locale.preferredLanguages).first
            ?? "en"
    }

    func resolve(_ sharedText: String) async throws -> ShareResolution {
        try await send("/share/resolve", body: ShareLinkRequest(url: sharedText), answer: ShareResolution.self)
    }

    func post(_ track: SharedTrack, caption: String) async throws {
        _ = try await send("/share/posts", body: SharePost(track: track, caption: caption), answer: Ignored.self)
    }

    private func send<Body: Encodable, Answer: Decodable>(_ path: String, body: Body, answer: Answer.Type) async throws -> Answer {
        guard let url = URL(string: baseURL + path) else { throw URLError(.badURL) }
        // Finding a Spotify song asks Apple and Spotify first: give it time.
        var request = URLRequest(url: url, timeoutInterval: 25)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(key, forHTTPHeaderField: "X-Share-Key")
        request.setValue(Self.language, forHTTPHeaderField: "Accept-Language")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 401 {
            throw Failure.signedOut
        }
        guard (200..<300).contains(status) else {
            let message = (try? JSONDecoder().decode(ServerMessage.self, from: data))?.message
            throw Failure.message(message ?? String(localized: "Something went wrong. Try again.", bundle: .share))
        }
        return try JSONDecoder().decode(Answer.self, from: data)
    }

    private struct ServerMessage: Decodable {
        let message: String
    }

    /// For answers whose content isn't needed.
    private struct Ignored: Decodable {}
}

/// The post made of a found song, as the server's PostRequest expects it.
private struct SharePost: Encodable {
    let musicId: String
    let musicProvider = "APPLE"
    let trackName: String
    let artistName: String
    let albumCoverUrl: String?
    let previewUrl: String?
    let appleMusicUrl: String?
    let spotifyUrl: String?
    let caption: String?

    init(track: SharedTrack, caption: String) {
        musicId = track.musicId
        trackName = String(track.trackName.prefix(255))
        artistName = String(track.artistName.prefix(255))
        albumCoverUrl = track.albumCoverUrl
        previewUrl = track.previewUrl
        appleMusicUrl = track.appleMusicUrl
        spotifyUrl = track.spotifyUrl
        let trimmed = caption.trimmingCharacters(in: .whitespacesAndNewlines)
        self.caption = trimmed.isEmpty ? nil : trimmed
    }
}

extension Bundle {
    /// The extension's texts in the language chosen in the app (texts put together in code; SwiftUI's own texts
    /// follow the view's locale).
    static let share: Bundle = Bundle.main.path(forResource: ShareAPI.language, ofType: "lproj")
        .flatMap(Bundle.init(path:)) ?? .main
}
