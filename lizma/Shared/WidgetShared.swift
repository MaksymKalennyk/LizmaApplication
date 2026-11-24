import Foundation

/// What the app and its home screen widget share through their App Group: the widget's key to the server, where the
/// server is, the language chosen in the app, and (in the container) the songs the widget showed last.
/// This file is part of both the app and the widget.
enum WidgetShared {
    static let appGroup = "group.maxos-industry.lizma"
    /// The widget's kind: the app asks WidgetKit to refresh it by this name.
    static let kind = "FriendsSongs"

    private static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    private enum Key {
        static let widgetKey = "widget.key"
        static let baseURL = "widget.baseURL"
        static let language = "widget.language"
    }

    /// Lets the widget read friends' songs (`GET /widget/feed`); nil while signed out. It works only while the
    /// app's sign-in on this phone lasts.
    static var widgetKey: String? {
        get { defaults?.string(forKey: Key.widgetKey) }
        set { defaults?.set(newValue, forKey: Key.widgetKey) }
    }

    /// The server the app talks to ("http://localhost:8080" in development).
    static var baseURL: String? {
        get { defaults?.string(forKey: Key.baseURL) }
        set { defaults?.set(newValue, forKey: Key.baseURL) }
    }

    /// "en" or "uk": the language chosen in the app (Profile → Language), which the widget speaks too.
    static var language: String? {
        get { defaults?.string(forKey: Key.language) }
        set { defaults?.set(newValue, forKey: Key.language) }
    }

    /// A folder both can write to.
    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
    }

    /// Opens the post in the app.
    static func postURL(_ id: Int) -> URL {
        URL(string: "lizma://post/\(id)")!
    }
}

/// `GET /widget/feed`: friends' latest songs, newest first.
struct WidgetFeedResponse: Codable {
    let posts: [WidgetFeedPost]
}

struct WidgetFeedPost: Codable, Identifiable, Hashable {
    let id: Int
    let authorUsername: String
    /// The display name, or the username without one.
    let authorName: String
    /// A path on the server ("/avatars/…"), nil without a photo.
    let authorAvatarUrl: String?
    let trackName: String
    let artistName: String
    let albumCoverUrl: String?
    let createdAt: String

    var postedAt: Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return withFraction.date(from: createdAt) ?? ISO8601DateFormatter().date(from: createdAt)
    }
}

/// `POST /user/me/widget-key`.
struct WidgetKeyResponse: Decodable {
    let key: String
}
