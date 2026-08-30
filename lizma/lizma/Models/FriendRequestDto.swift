import Foundation

struct FriendRequestDto: Codable, Identifiable {
    let id: Int64
    let requesterId: Int64
    let requesterUsername: String
    let recipientId: Int64
    let recipientUsername: String
    let status: String
}

enum FriendRequestStatus: String, Codable, CaseIterable {
    case pending = "PENDING"
    case accepted = "ACCEPTED"
    case declined = "DECLINED"
}
