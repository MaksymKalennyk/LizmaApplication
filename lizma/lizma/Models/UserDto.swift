import Foundation

struct UserDto: Codable, Identifiable {
    let id: Int64
    let username: String
    let displayName: String?
    let avatarUrl: String?
}
