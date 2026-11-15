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
}

enum ReportTargetType: String, Encodable {
    case post = "POST"
    case comment = "COMMENT"
    case user = "USER"
}

enum ReportReason: String, CaseIterable, Identifiable, Encodable {
    case spam = "SPAM"
    case harassment = "HARASSMENT"
    case hate = "HATE"
    case nudity = "NUDITY"
    case violence = "VIOLENCE"
    case other = "OTHER"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .spam: return "Spam or scam"
        case .harassment: return "Harassment or bullying"
        case .hate: return "Hate speech or symbols"
        case .nudity: return "Nudity or sexual content"
        case .violence: return "Violence or threats"
        case .other: return "Something else"
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
