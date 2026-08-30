import Foundation

struct PostResponse: Codable, Identifiable {
    let id: Int
    let authorId: Int
    let authorUsername: String
    let musicId: String
    let musicProvider: String
    let trackName: String
    let artistName: String
    let albumCoverUrl: String?
    let createdAt: String
    let caption: String?
    var reactionsCount: [String: Int]
    let commentsCount: Int
    var currentUserReaction: String?

    enum CodingKeys: String, CodingKey {
        case id, authorId, authorUsername, musicId, musicProvider, trackName, artistName, albumCoverUrl, createdAt, caption, reactionsCount, commentsCount
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
}

struct CommentResponse: Codable, Identifiable {
    let id: Int
    let authorId: Int
    let authorUsername: String
    let content: String
    let createdAt: String
    let parentId: Int?
    var likesCount: Int
    var isLikedByCurrentUser: Bool
    var replies: [CommentResponse]?
    
    enum CodingKeys: String, CodingKey {
        case id, authorId, authorUsername, content, createdAt, parentId, likesCount, replies
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
    let type: String
    let createdAt: String?
    
    var id: String {
        return "\(authorId)_\(type)"
    }
}

struct SpotifyTrack: Codable, Identifiable {
    let id: String
    let name: String
    let album: SpotifyAlbum
    let artists: [SpotifyArtist]
}

struct SpotifyAlbum: Codable {
    let images: [SpotifyImage]?
}

struct SpotifyArtist: Codable {
    let name: String
}

struct SpotifyImage: Codable {
    let url: String
}
