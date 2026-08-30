import Foundation

struct UserDto: Codable, Identifiable {
    let id: Int64
    let username: String
}
