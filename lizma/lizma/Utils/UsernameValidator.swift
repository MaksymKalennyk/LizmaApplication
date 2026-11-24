import Foundation

/// Mirrors the backend's username rules: `^[A-Za-z0-9_.-]{5,50}$`.
enum UsernameValidator {
    static let lengthRange = 5...50
    // Computed, not stored: they follow a language change in Profile → Language.
    static var lengthMessage: String { String(localized: "Username must be between 5 and 50 characters", bundle: .app) }
    static var charactersMessage: String { String(localized: "Username can contain only letters, digits, '.', '_' and '-'", bundle: .app) }

    private static let allowedCharacters = CharacterSet(
        charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-"
    )

    static func hasOnlyAllowedCharacters(_ username: String) -> Bool {
        username.unicodeScalars.allSatisfy { allowedCharacters.contains($0) }
    }

    static func isValid(_ username: String) -> Bool {
        lengthRange.contains(username.count) && hasOnlyAllowedCharacters(username)
    }
}
