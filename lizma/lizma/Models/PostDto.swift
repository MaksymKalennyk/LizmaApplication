import Foundation

struct PostResponse: Codable, Identifiable {
    let id: Int
    let authorId: Int
    let authorUsername: String
    /// Changeable here so your own posts can follow an edited name or photo without reloading.
    var authorDisplayName: String?
    var authorAvatarUrl: String?
    let musicId: String
    let musicProvider: String
    let trackName: String
    let artistName: String
    let albumCoverUrl: String?
    let createdAt: String
    let caption: String?
    /// A 30-second preview anyone can play; the track in Apple Music and in Spotify.
    let previewUrl: String?
    let appleMusicUrl: String?
    let spotifyUrl: String?
    var reactionsCount: [String: Int]
    var commentsCount: Int
    var currentUserReaction: String?

    enum CodingKeys: String, CodingKey {
        case id, authorId, authorUsername, authorDisplayName, authorAvatarUrl, musicId, musicProvider, trackName, artistName,
             albumCoverUrl, createdAt, caption, previewUrl, appleMusicUrl, spotifyUrl, reactionsCount, commentsCount
        case currentUserReaction
    }
}

struct PostRequest: Codable {
    let musicId: String
    let musicProvider: String
    let trackName: String
    let artistName: String
    let albumCoverUrl: String?
    let caption: String?
    let previewUrl: String?
    let appleMusicUrl: String?
    /// The exact Spotify track, for a song found from a Spotify link.
    var spotifyUrl: String? = nil
}

struct CommentResponse: Codable, Identifiable {
    let id: Int
    let authorId: Int
    let authorUsername: String
    let authorDisplayName: String?
    let authorAvatarUrl: String?
    let content: String
    let createdAt: String
    let parentId: Int?
    var likesCount: Int
    var isLikedByCurrentUser: Bool
    var replies: [CommentResponse]?
    
    enum CodingKeys: String, CodingKey {
        case id, authorId, authorUsername, authorDisplayName, authorAvatarUrl, content, createdAt, parentId, likesCount, replies
        case isLikedByCurrentUser = "likedByCurrentUser"
    }
}

struct CommentRequest: Codable {
    let content: String
    let parentId: Int?
}

struct ReactionDetailResponse: Codable, Identifiable {
    let authorId: Int
    let authorUsername: String
    let authorDisplayName: String?
    let authorAvatarUrl: String?
    let type: String
    let createdAt: String?
    
    var id: String {
        return "\(authorId)_\(type)"
    }
}

