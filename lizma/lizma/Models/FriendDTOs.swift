import Foundation

/// A single-use friend invite from `POST /user/friend-invites` (works once, expires after 7 days).
struct InviteLinkDto: Codable {
    let token: String?
    let url: String?
    let expiresAt: String?

    /// The link to share: the server's URL, or one built from the token if the URL is missing.
    var shareURL: URL? {
        if let url, let parsed = URL(string: url) { return parsed }
        guard let token, !token.isEmpty,
              let segment = token.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed)
        else { return nil }
        return URL(string: AppConfig.apiBaseURL + "/i/" + segment)
    }
}
