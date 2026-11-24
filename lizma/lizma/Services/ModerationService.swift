import Foundation

/// The signed-in user's moderator status, the feedback form, and the moderators' `/admin` endpoints.
final class ModerationService {
    static let shared = ModerationService()
    private init() {}

    func me() async throws -> MeResponse {
        try await APIClient.shared.send(MeResponse.self, path: "/user/me")
    }

    /// Returns the server's thank-you message.
    func sendFeedback(_ request: FeedbackRequest) async throws -> String {
        try await APIClient.shared.send(MessageResponse.self, path: "/user/feedback", method: "POST", body: request).message
    }

    // MARK: Admin

    func summary() async throws -> AdminSummary {
        try await APIClient.shared.send(AdminSummary.self, path: "/admin/summary")
    }

    /// Open reports oldest first, or the last handled ones newest first.
    func reports(handled: Bool) async throws -> [AdminReport] {
        let statuses: [ReportStatus] = handled ? [.resolved, .dismissed] : [.open]
        return try await APIClient.shared.send([AdminReport].self, path: "/admin/reports",
                                               query: statuses.map { URLQueryItem(name: "status", value: $0.rawValue) })
    }

    func resolve(reportId: Int64, action: ReportAction) async throws {
        struct Body: Encodable { let action: ReportAction }
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/admin/reports/\(reportId)/resolve",
                                            method: "POST", body: Body(action: action))
    }

    func feedback(done: Bool) async throws -> [FeedbackItem] {
        try await APIClient.shared.send([FeedbackItem].self, path: "/admin/feedback",
                                        query: [URLQueryItem(name: "status", value: done ? "DONE" : "NEW")])
    }

    func markFeedback(id: Int64, done: Bool) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self,
                                            path: "/admin/feedback/\(id)/\(done ? "done" : "reopen")",
                                            method: "POST")
    }

    /// Users whose name contains the query, suspended ones included; with an empty query, the suspended users.
    func users(matching query: String) async throws -> [AdminUser] {
        try await APIClient.shared.send([AdminUser].self, path: "/admin/users",
                                        query: [URLQueryItem(name: "query", value: query)])
    }

    func ban(username: String) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/admin/users/\(segment(username))/ban", method: "POST")
    }

    func unban(username: String) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/admin/users/\(segment(username))/unban", method: "POST")
    }

    func deleteAccount(username: String) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/admin/users/\(segment(username))", method: "DELETE")
    }

    func log() async throws -> [ModerationLogEntry] {
        try await APIClient.shared.send([ModerationLogEntry].self, path: "/admin/log")
    }

    private func segment(_ username: String) -> String {
        username.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed) ?? username
    }
}
