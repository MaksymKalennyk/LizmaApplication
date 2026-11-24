import Foundation

enum DeepLink {
    private static let pendingInviteKey = "pendingInviteToken"
    /// Older builds stored an inviter's username here; usernames are no longer accepted as invites.
    private static let legacyPendingKey = "pendingInviteUsername"

    private static let tokenLength = 20...64
    private static let tokenCharacters = CharacterSet(
        charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"
    )

    /// Parses an invite link `lizma://i/<token>`. Anything else is not an invite.
    static func parseInviteToken(from url: URL) -> String? {
        guard url.scheme?.lowercased() == "lizma", url.host?.lowercased() == "i" else { return nil }
        let comps = url.pathComponents.filter { $0 != "/" }
        guard comps.count == 1, let token = comps.first, isValidToken(token) else { return nil }
        return token
    }

    /// A post opened from the home screen widget: `lizma://post/<id>`.
    static func parsePostId(from url: URL) -> Int? {
        guard url.scheme?.lowercased() == "lizma", url.host?.lowercased() == "post" else { return nil }
        let comps = url.pathComponents.filter { $0 != "/" }
        guard comps.count == 1, let id = comps.first.flatMap({ Int($0) }), id > 0 else { return nil }
        return id
    }

    /// Invite tokens are base64url: `^[A-Za-z0-9_-]{20,64}$`.
    static func isValidToken(_ token: String) -> Bool {
        tokenLength.contains(token.count) && token.unicodeScalars.allSatisfy { tokenCharacters.contains($0) }
    }

    /// Keeps an invite opened while signed out, to accept it after signing in.
    static func storePendingInvite(_ token: String) {
        UserDefaults.standard.set(token, forKey: pendingInviteKey)
    }

    static func takePendingInvite() -> String? {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: legacyPendingKey)
        guard let token = defaults.string(forKey: pendingInviteKey) else { return nil }
        defaults.removeObject(forKey: pendingInviteKey)
        return isValidToken(token) ? token : nil
    }
}
