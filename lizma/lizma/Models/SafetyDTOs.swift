import Foundation

/// `GET /legal`: where the legal pages live and how to reach support.
struct LegalInfo: Decodable {
    let contactEmail: String?
    let privacyPolicyUrl: String?
    let termsUrl: String?
}

/// An entry of `GET /user/blocks`.
struct BlockedUserDto: Decodable, Identifiable {
    let id: Int64
    let username: String
    let displayName: String?
    let avatarUrl: String?
}

enum ReportTargetType: String, Codable {
    case post = "POST"
    case comment = "COMMENT"
    case user = "USER"
}

enum ReportReason: String, CaseIterable, Identifiable, Codable {
    case spam = "SPAM"
    case harassment = "HARASSMENT"
    case hate = "HATE"
    case nudity = "NUDITY"
    case violence = "VIOLENCE"
    case other = "OTHER"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .spam: return String(localized: "Spam or scam", bundle: .app)
        case .harassment: return String(localized: "Harassment or bullying", bundle: .app)
        case .hate: return String(localized: "Hate speech or symbols", bundle: .app)
        case .nudity: return String(localized: "Nudity or sexual content", bundle: .app)
        case .violence: return String(localized: "Violence or threats", bundle: .app)
        case .other: return String(localized: "Something else", bundle: .app)
        }
    }
}

/// `POST /reports`.
struct ReportRequest: Encodable {
    let targetType: ReportTargetType
    let targetId: Int64
    let reason: ReportReason
    let details: String?
}

/// `POST /user/me/delete`.
struct DeleteAccountRequest: Encodable {
    let password: String
}
