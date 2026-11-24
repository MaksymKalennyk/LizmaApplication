import Foundation

/// Sign-in, sign-up and refresh: a short-lived access token (JWT) and the refresh token that renews it.
struct JwtAuthenticationResponse: Codable {
    let token: String
    /// Single use: every refresh returns a new one.
    let refreshToken: String?
    /// Seconds until `token` expires.
    let expiresIn: Int?
}

/// `POST /auth/refresh`.
struct RefreshRequest: Encodable {
    let refreshToken: String
}

/// `POST /auth/logout`: the session to end and the phone that stops getting this user's notifications.
struct LogoutRequest: Encodable {
    let refreshToken: String
    let deviceToken: String?
}
