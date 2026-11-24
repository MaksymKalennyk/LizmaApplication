import OSLog
import UIKit
import WidgetKit

/// A friend's song, ready to draw: the pictures are downloaded beforehand, a widget can't load them while drawing.
struct WidgetSong: Identifiable {
    let id: Int
    let authorName: String
    let trackName: String
    let artistName: String
    let postedAt: Date?
    let cover: UIImage?
    let avatar: UIImage?

    var url: URL { WidgetShared.postURL(id) }
}

struct FriendsSongsEntry: TimelineEntry {
    enum Content {
        /// No sign-in on this phone (or it ended): the widget asks to open the app.
        case signedOut
        /// Friends' latest songs, newest first; none yet is a state of its own.
        case songs([WidgetSong])
    }

    let date: Date
    let content: Content
    /// "en" or "uk", as chosen in the app.
    let language: String

    /// For the widget gallery and while loading: made-up songs without pictures (each with an id of its own:
    /// the tiles are drawn one per id).
    static let sample = FriendsSongsEntry(date: .now, content: .songs([
        WidgetSong(id: -1, authorName: "Liza", trackName: "Blinding Lights", artistName: "The Weeknd",
                   postedAt: .now.addingTimeInterval(-600), cover: nil, avatar: nil),
        WidgetSong(id: -2, authorName: "Max", trackName: "Bohemian Rhapsody", artistName: "Queen",
                   postedAt: .now.addingTimeInterval(-3_600), cover: nil, avatar: nil),
        WidgetSong(id: -3, authorName: "Ann", trackName: "Levitating", artistName: "Dua Lipa",
                   postedAt: .now.addingTimeInterval(-7_200), cover: nil, avatar: nil),
        WidgetSong(id: -4, authorName: "Bob", trackName: "Yellow", artistName: "Coldplay",
                   postedAt: .now.addingTimeInterval(-10_800), cover: nil, avatar: nil),
    ]), language: FriendsSongsLoader.language)
}

struct FriendsSongsProvider: TimelineProvider {
    /// iOS decides when to refresh; this asks for about every 15 minutes. The app asks for more when it has news.
    static let refreshInterval: TimeInterval = 15 * 60

    func placeholder(in context: Context) -> FriendsSongsEntry {
        .sample
    }

    func getSnapshot(in context: Context, completion: @escaping (FriendsSongsEntry) -> Void) {
        if context.isPreview {
            // The widget gallery: the songs shown last, or the made-up ones.
            completion(FriendsSongsLoader.cached(family: context.family) ?? .sample)
            return
        }
        Task { completion(await FriendsSongsLoader.load(family: context.family)) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FriendsSongsEntry>) -> Void) {
        Task {
            let entry = await FriendsSongsLoader.load(family: context.family)
            completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(Self.refreshInterval))))
        }
    }
}

/// Fetches friends' songs with the widget's key, downloads their pictures, and keeps the last ones for when the
/// phone is offline.
enum FriendsSongsLoader {
    private static let log = Logger(subsystem: "maxos-industry.lizma.LizmaWidget", category: "widget")

    static var language: String {
        WidgetShared.language
            ?? Bundle.preferredLocalizations(from: ["en", "uk"], forPreferences: Locale.preferredLanguages).first
            ?? "en"
    }

    static func load(family: WidgetFamily) async -> FriendsSongsEntry {
        guard let key = WidgetShared.widgetKey, let base = WidgetShared.baseURL else {
            return FriendsSongsEntry(date: .now, content: .signedOut, language: language)
        }
        do {
            // All of them (the server sends at most four), so the cache serves every size.
            let posts = try await fetch(base: base, key: key)
            let songs = await withPictures(posts, base: base)
            Cache.save(posts: posts, songs: songs)
            return FriendsSongsEntry(date: .now, content: .songs(Array(songs.prefix(family.songCount))), language: language)
        } catch FetchError.signedOut {
            // The app's sign-in on this phone has ended: forget the key until it signs in again.
            WidgetShared.widgetKey = nil
            Cache.clear()
            return FriendsSongsEntry(date: .now, content: .signedOut, language: language)
        } catch {
            log.notice("Couldn't load friends' songs: \(error.localizedDescription, privacy: .public)")
            return cached(family: family) ?? FriendsSongsEntry(date: .now, content: .songs([]), language: language)
        }
    }

    /// The songs shown last, if any.
    static func cached(family: WidgetFamily) -> FriendsSongsEntry? {
        guard WidgetShared.widgetKey != nil, let songs = Cache.load(limit: family.songCount) else { return nil }
        return FriendsSongsEntry(date: .now, content: .songs(songs), language: language)
    }

    private enum FetchError: Error {
        case signedOut
        case server(Int)
    }

    private static func fetch(base: String, key: String) async throws -> [WidgetFeedPost] {
        guard let url = URL(string: base + "/widget/feed") else { throw URLError(.badURL) }
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue(key, forHTTPHeaderField: "X-Widget-Key")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 401 { throw FetchError.signedOut }
        guard (200..<300).contains(status) else { throw FetchError.server(status) }
        return try JSONDecoder().decode(WidgetFeedResponse.self, from: data).posts
    }

    private static func withPictures(_ posts: [WidgetFeedPost], base: String) async -> [WidgetSong] {
        await withTaskGroup(of: (Int, WidgetSong).self) { group in
            for (index, post) in posts.enumerated() {
                group.addTask {
                    async let cover = picture(post.albumCoverUrl.flatMap(URL.init(string:)), side: 420)
                    async let avatar = picture(post.authorAvatarUrl.flatMap { URL(string: base + $0) }, side: 80)
                    return (index, WidgetSong(id: post.id, authorName: post.authorName, trackName: post.trackName,
                                              artistName: post.artistName, postedAt: post.postedAt,
                                              cover: await cover, avatar: await avatar))
                }
            }
            var songs: [(Int, WidgetSong)] = []
            for await song in group { songs.append(song) }
            return songs.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    /// A small copy of the picture: widgets have little memory.
    private static func picture(_ url: URL?, side: CGFloat) async -> UIImage? {
        guard let url, let (data, response) = try? await URLSession.shared.data(from: url),
              ((response as? HTTPURLResponse)?.statusCode ?? 0) == 200,
              let image = UIImage(data: data) else { return nil }
        let scale = min(1, side / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        return image.preparingThumbnail(of: size) ?? image
    }

    /// The last songs and their pictures, in the App Group container.
    private enum Cache {
        private static var folder: URL? {
            WidgetShared.containerURL?.appendingPathComponent("widget-cache", isDirectory: true)
        }

        static func save(posts: [WidgetFeedPost], songs: [WidgetSong]) {
            guard let folder else { return }
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try? JSONEncoder().encode(WidgetFeedResponse(posts: posts)).write(to: folder.appendingPathComponent("feed.json"))
            for song in songs {
                if let data = song.cover?.jpegData(compressionQuality: 0.8) {
                    try? data.write(to: folder.appendingPathComponent("cover-\(song.id).jpg"))
                }
                if let data = song.avatar?.jpegData(compressionQuality: 0.8) {
                    try? data.write(to: folder.appendingPathComponent("avatar-\(song.id).jpg"))
                }
            }
        }

        static func load(limit: Int) -> [WidgetSong]? {
            guard let folder,
                  let data = try? Data(contentsOf: folder.appendingPathComponent("feed.json")),
                  let feed = try? JSONDecoder().decode(WidgetFeedResponse.self, from: data) else { return nil }
            return feed.posts.prefix(limit).map { post in
                WidgetSong(id: post.id, authorName: post.authorName, trackName: post.trackName,
                           artistName: post.artistName, postedAt: post.postedAt,
                           cover: UIImage(contentsOfFile: folder.appendingPathComponent("cover-\(post.id).jpg").path),
                           avatar: UIImage(contentsOfFile: folder.appendingPathComponent("avatar-\(post.id).jpg").path))
            }
        }

        static func clear() {
            guard let folder else { return }
            try? FileManager.default.removeItem(at: folder)
        }
    }
}

extension WidgetFamily {
    /// How many songs this size shows.
    var songCount: Int {
        switch self {
        case .systemMedium: return 2
        case .systemLarge, .systemExtraLarge: return 4
        default: return 1
        }
    }
}
