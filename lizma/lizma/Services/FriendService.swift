import Foundation

final class FriendService {
    static let shared = FriendService()
    private init() {}

    /// Server reply when the recipient had already asked us: the request was accepted instead.
    static let becameFriendsReply = "You are now friends!"

    func listFriends() async throws -> [FriendshipDto] {
        try await APIClient.shared.send([FriendshipDto].self, path: "/user/friends")
    }

    func listReceivedRequests() async throws -> [FriendRequestDto] {
        try await APIClient.shared.send([FriendRequestDto].self, path: "/user/friend-requests/received")
    }

    func listSentRequests() async throws -> [FriendRequestDto] {
        try await APIClient.shared.send([FriendRequestDto].self, path: "/user/friend-requests/sent")
    }

    /// Returns the server's reply: "Request sent." or `becameFriendsReply`.
    @discardableResult
    func sendRequest(to recipientId: Int64) async throws -> String {
        try await APIClient.shared.send(String.self,
                                        path: "/user/friend-requests/send/\(recipientId)",
                                        method: "POST")
    }

    func respond(to requestId: Int64, status: FriendRequestStatus) async throws {
        _ = try await APIClient.shared.send(Empty.self,
                                            path: "/user/friend-requests/respond/\(requestId)",
                                            method: "PUT",
                                            query: [URLQueryItem(name: "status", value: status.rawValue)])
    }

    /// Takes back a request the user sent that hasn't been answered yet.
    func cancelRequest(_ requestId: Int64) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/user/friend-requests/\(requestId)", method: "DELETE")
    }

    /// Ends the friendship (the other person isn't told). Either side can send a new request later.
    func removeFriend(username: String) async throws {
        let segment = username.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed) ?? username
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/user/friends/\(segment)", method: "DELETE")
    }

    /// Creates a new single-use invite link (every call makes a fresh one; it expires after 7 days).
    func createInvite() async throws -> InviteLinkDto {
        try await APIClient.shared.send(InviteLinkDto.self,
                                        path: "/user/friend-invites",
                                        method: "POST")
    }

    /// Accepts an invite; returns the server's message ("Friendship established" / "You are already friends").
    func acceptInvite(token: String) async throws -> String {
        let segment = token.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed) ?? token
        let response = try await APIClient.shared.send(MessageResponse.self,
                                                       path: "/user/friend-invites/\(segment)/accept",
                                                       method: "POST")
        return response.message
    }
}

extension CharacterSet {
    /// Unreserved characters (RFC 3986): everything else is percent-encoded, including "/", "?" and "#".
    static let urlPathSegmentAllowed = CharacterSet(
        charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    )
}
