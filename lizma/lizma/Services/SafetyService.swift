import Foundation

/// Legal info, blocking and reporting (App Store guideline 1.2 for user-generated content).
final class SafetyService {
    static let shared = SafetyService()
    private init() {}

    /// Public: works signed out too (sign-up screen).
    func legalInfo() async throws -> LegalInfo {
        try await APIClient.shared.send(LegalInfo.self, path: "/legal")
    }

    func blockedUsers() async throws -> [BlockedUserDto] {
        try await APIClient.shared.send([BlockedUserDto].self, path: "/user/blocks")
    }

    /// Also removes the friendship and pending requests between the two users.
    @discardableResult
    func block(userId: Int64) async throws -> String {
        try await APIClient.shared.send(MessageResponse.self,
                                        path: "/user/blocks/\(userId)",
                                        method: "POST").message
    }

    @discardableResult
    func unblock(userId: Int64) async throws -> String {
        try await APIClient.shared.send(MessageResponse.self,
                                        path: "/user/blocks/\(userId)",
                                        method: "DELETE").message
    }

    /// Returns the server's confirmation message.
    func report(_ report: ReportRequest) async throws -> String {
        try await APIClient.shared.send(MessageResponse.self,
                                        path: "/reports",
                                        method: "POST",
                                        body: report).message
    }
}
