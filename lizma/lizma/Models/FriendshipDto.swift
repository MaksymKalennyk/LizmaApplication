import Foundation

struct FriendshipDto: Codable, Identifiable {
    let id: Int64
    let friendId: Int64
    let friendUsername: String
}
