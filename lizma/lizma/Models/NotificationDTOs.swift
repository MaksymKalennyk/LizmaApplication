import Foundation

/// What the user gets push notifications about (Profile → Notifications).
struct NotificationSettings: Codable, Equatable {
    var friendRequests: Bool
    var reactions: Bool
    var comments: Bool
    var dailyReminder: Bool
}

/// `PUT /user/me/devices`: this phone's APNs token and which APNs it belongs to.
struct DeviceRegistration: Encodable {
    let token: String
    /// SANDBOX for development builds, PRODUCTION for TestFlight and the App Store.
    let environment: String
}

/// Where a tapped notification leads (the server puts `type` and ids next to the alert).
enum PushRoute: Equatable, Sendable {
    /// Someone wants to be friends: the Friends tab, where requests wait.
    case friendRequests
    /// A new friend: their profile.
    case profile(username: String)
    /// A reaction (the post) or a comment or reply (the post with its comments).
    case post(id: Int, showComments: Bool)
    /// The daily reminder: the composer.
    case composer

    init?(userInfo: [AnyHashable: Any]) {
        switch userInfo["type"] as? String {
        case "friend_request":
            self = .friendRequests
        case "new_friend":
            guard let username = userInfo["username"] as? String else { return nil }
            self = .profile(username: username)
        case "reaction":
            guard let id = Self.number(userInfo["postId"]) else { return nil }
            self = .post(id: id, showComments: false)
        case "comment", "reply":
            guard let id = Self.number(userInfo["postId"]) else { return nil }
            self = .post(id: id, showComments: true)
        case "daily_reminder":
            self = .composer
        default:
            return nil
        }
    }

    /// About friends: the requests and the friend list are out of date.
    var changesFriends: Bool {
        switch self {
        case .friendRequests, .profile: return true
        default: return false
        }
    }

    private static func number(_ value: Any?) -> Int? {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }
}
