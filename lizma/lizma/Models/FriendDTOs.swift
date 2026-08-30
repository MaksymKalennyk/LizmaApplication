import Foundation

struct InviteLinkDto: Codable {
    let token: String?
    let url: String
    let expiresAt: String?
}
