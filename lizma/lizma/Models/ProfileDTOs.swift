import Foundation

/// `GET /user/profiles/{username}`: a user as the viewer sees them.
struct ProfileResponse: Decodable, Equatable {
    enum Relationship: String, Decodable {
        case selfProfile = "SELF"
        case friend = "FRIEND"
        case requestSent = "REQUEST_SENT"
        case requestReceived = "REQUEST_RECEIVED"
        case none = "NONE"
    }

    let id: Int64
    let username: String
    let displayName: String?
    let avatarUrl: String?
    let relationship: Relationship
    /// The pending friend request between the two, to cancel (sent) or answer (received) it.
    let requestId: Int64?
    /// Posts are for friends and yourself only.
    let canSeePosts: Bool
    let postsCount: Int?

    /// The name to show: the display name if set, else the username.
    var name: String { displayName ?? username }
}

/// `PUT /user/me/profile`.
struct DisplayNameRequest: Encodable {
    let displayName: String?
}

/// `PUT /user/me/avatar`.
struct AvatarResponse: Decodable {
    let avatarUrl: String
}

extension PostResponse {
    /// The author's display name if set, else the username.
    var authorName: String { authorDisplayName ?? authorUsername }
}

extension CommentResponse {
    var authorName: String { authorDisplayName ?? authorUsername }
}

extension PostResponse {
    /// The post after the viewer taps `emoji`: the same emoji again removes their reaction, another replaces
    /// it (as on the server).
    func togglingReaction(_ emoji: String) -> PostResponse {
        var copy = self
        var counts = reactionsCount
        if let previous = currentUserReaction {
            let remaining = (counts[previous] ?? 0) - 1
            counts[previous] = remaining > 0 ? remaining : nil
        }
        let newReaction: String? = currentUserReaction == emoji ? nil : emoji
        if let newReaction {
            counts[newReaction, default: 0] += 1
        }
        copy.currentUserReaction = newReaction
        copy.reactionsCount = counts
        return copy
    }
}
