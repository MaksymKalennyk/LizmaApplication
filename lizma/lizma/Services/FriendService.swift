import Foundation

final class FriendService {
    static let shared = FriendService()
    private init() {}

    func listFriends() async throws -> [FriendshipDto] {
        try await APIClient.shared.send([FriendshipDto].self, path: "/user/friends")
    }

    func listReceivedRequests() async throws -> [FriendRequestDto] {
        try await APIClient.shared.send([FriendRequestDto].self, path: "/user/friend-requests/received")
    }

    func listSentRequests() async throws -> [FriendRequestDto] {
        try await APIClient.shared.send([FriendRequestDto].self, path: "/user/friend-requests/sent")
    }

    func sendRequest(to recipientId: Int64) async throws {
        _ = try await APIClient.shared.send(Empty.self,
                                            path: "/user/friend-requests/send/\(recipientId)",
                                            method: "POST")
    }

    func respond(to requestId: Int64, status: FriendRequestStatus) async throws {
        _ = try await APIClient.shared.send(Empty.self,
                                            path: "/user/friend-requests/respond/\(requestId)",
                                            method: "PUT",
                                            query: [URLQueryItem(name: "status", value: status.rawValue)])
    }

    func createVanityInviteLink() async throws -> InviteLinkDto {
        try await APIClient.shared.send(InviteLinkDto.self,
                                        path: "/user/friend-invites/vanity-link",
                                        method: "POST")
    }

    func acceptInvite(byUsername username: String) async throws {
        _ = try await APIClient.shared.send(Empty.self,
                                            path: "/user/friend-invites/accept-by-username/\(username)",
                                            method: "POST")
    }
}
