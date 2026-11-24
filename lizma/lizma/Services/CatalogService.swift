import Foundation

/// A song from the Apple Music catalog, as the iTunes Search API returns it.
struct CatalogTrack: Decodable, Identifiable, Equatable {
    let trackId: Int
    let trackName: String
    let artistName: String
    let collectionName: String?
    let artworkUrl100: String?
    let previewUrl: String?
    let trackViewUrl: String?

    var id: Int { trackId }

    /// The cover at 600×600 (the API gives 100×100; the CDN makes any size from the same path).
    var artworkURL: String? {
        artworkUrl100?.replacingOccurrences(of: "100x100bb", with: "600x600bb")
    }

    /// The track's Apple Music page without the affiliate parameter.
    var appleMusicURL: String? {
        guard let trackViewUrl, var components = URLComponents(string: trackViewUrl) else { return nil }
        components.queryItems = components.queryItems?.filter { $0.name != "uo" }
        if components.queryItems?.isEmpty == true { components.queryItems = nil }
        return components.url?.absoluteString
    }
}

/// Searches songs in Apple's catalog straight from the phone: no account, no subscription, no key.
/// The search text goes to Apple, not to our server.
final class CatalogService {
    static let shared = CatalogService()
    private init() {}

    private struct SearchResponse: Decodable {
        let results: [CatalogTrack]
    }

    func search(_ term: String, limit: Int = 25) async throws -> [CatalogTrack] {
        var components = URLComponents(string: "https://itunes.apple.com/search")!
        components.queryItems = [
            URLQueryItem(name: "term", value: term),
            URLQueryItem(name: "media", value: "music"),
            URLQueryItem(name: "entity", value: "song"),
            URLQueryItem(name: "limit", value: String(limit)),
            // The store of the phone's region: its links and previews work there.
            URLQueryItem(name: "country", value: Locale.current.region?.identifier ?? "US"),
        ]
        guard let url = components.url else { throw APIError.invalidURL }
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIError.server(code: (response as? HTTPURLResponse)?.statusCode ?? 0,
                                  message: String(localized: "Music search is unavailable right now. Try again in a minute.", bundle: .app))
        }
        do {
            return try JSONDecoder().decode(SearchResponse.self, from: data).results
        } catch {
            throw APIError.decodingFailed
        }
    }
}
