import Foundation

/// `GET /user/me`: who is signed in and whether they moderate.
struct MeResponse: Decodable {
    let id: Int64
    let username: String
    let admin: Bool
    let displayName: String?
    let avatarUrl: String?
}

// MARK: - Feedback

enum FeedbackCategory: String, Codable, CaseIterable, Identifiable {
    case idea = "IDEA"
    case problem = "PROBLEM"
    case other = "OTHER"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .idea: return String(localized: "Idea", bundle: .app)
        case .problem: return String(localized: "Problem", bundle: .app)
        case .other: return String(localized: "Other", bundle: .app, comment: "A feedback topic")
        }
    }

    var systemImage: String {
        switch self {
        case .idea: return "lightbulb"
        case .problem: return "exclamationmark.triangle"
        case .other: return "bubble.left"
        }
    }
}

/// `POST /user/feedback`.
struct FeedbackRequest: Encodable {
    let category: FeedbackCategory
    let message: String
    let contactEmail: String?
    let appVersion: String?
    let osVersion: String?
    /// Screenshots uploaded before (`AttachmentService`), at most three.
    var attachmentIds: [String] = []
}

enum FeedbackStatus: String, Decodable {
    case new = "NEW"
    case done = "DONE"
}

/// Feedback as moderators see it (`GET /admin/feedback`).
struct FeedbackItem: Decodable, Identifiable, Hashable {
    let id: Int64
    let category: FeedbackCategory
    let message: String
    let authorUsername: String
    let contactEmail: String?
    let appVersion: String?
    let osVersion: String?
    let status: FeedbackStatus
    let createdAt: String
    let handledAt: String?
    let handledByUsername: String?
    /// Screenshots, paths under /admin/attachments (moderators only); missing from older servers.
    let attachments: [String]?
}

// MARK: - Reports

enum ReportStatus: String, Decodable {
    case open = "OPEN"
    case resolved = "RESOLVED"
    case dismissed = "DISMISSED"
}

/// What a moderator does with a report.
enum ReportAction: String, Codable {
    case removeContent = "REMOVE_CONTENT"
    case banUser = "BAN_USER"
    case dismiss = "DISMISS"
}

/// A report as moderators see it (`GET /admin/reports`).
struct AdminReport: Decodable, Identifiable, Hashable {
    let id: Int64
    let targetType: ReportTargetType
    let targetId: Int64
    /// The author of the post or comment, or the reported user; nil if that account is gone.
    let targetUsername: String?
    /// The post or comment text; nil once the content is deleted.
    let targetPreview: String?
    let targetArtworkUrl: String?
    let reason: ReportReason
    let details: String?
    let reporterUsername: String
    let status: ReportStatus
    let createdAt: String
    let resolvedAt: String?
    let resolution: ReportAction?
    let resolvedByUsername: String?
    /// Screenshots the reporter attached, paths under /admin/attachments (moderators only).
    let attachments: [String]?
}

extension ReportTargetType: Hashable {}
extension ReportReason: Hashable {}

/// `GET /admin/summary`: what waits for moderators.
struct AdminSummary: Decodable, Equatable {
    let openReports: Int
    let newFeedback: Int

    var total: Int { openReports + newFeedback }
}

// MARK: - Users and log

/// `GET /admin/users`.
struct AdminUser: Decodable, Identifiable, Hashable {
    let id: Int64
    let username: String
    let displayName: String?
    let avatarUrl: String?
    let banned: Bool
    let admin: Bool
}

enum ModerationActionType: String, Decodable {
    case removeContent = "REMOVE_CONTENT"
    case banUser = "BAN_USER"
    case unbanUser = "UNBAN_USER"
    case deleteUser = "DELETE_USER"
    case dismissReport = "DISMISS_REPORT"
}

/// `GET /admin/log`.
struct ModerationLogEntry: Decodable, Identifiable {
    let id: Int64
    let action: ModerationActionType
    /// Nil if that moderator's account no longer exists.
    let moderatorUsername: String?
    let targetUsername: String?
    let reportId: Int64?
    let createdAt: String
}
