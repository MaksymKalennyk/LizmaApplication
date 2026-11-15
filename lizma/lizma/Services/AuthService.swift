import Foundation

final class AuthService {
    static let shared = AuthService()

    private static let tokenKey = "jwtToken"
    private static let installMarkerKey = "lizma.hasLaunchedBefore"

    private init() {
        // Keychain items survive deleting the app: a fresh install must not inherit the previous install's session.
        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: Self.installMarkerKey) {
            Keychain.delete(Self.tokenKey)
            defaults.set(true, forKey: Self.installMarkerKey)
        }
    }

    /// The stored JWT, if any.
    var token: String? { try? Keychain.get(Self.tokenKey) }

    /// Username of the signed-in user, read from the token.
    var currentUsername: String? { token.flatMap(JWT.username(from:)) }

    func signIn(username: String, password: String) async throws {
        let req = SignInRequest(username: username, password: password)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-in",
                                                       method: "POST",
                                                       body: req)
        try Keychain.set(response.token, forKey: Self.tokenKey)
    }

    func signUp(username: String, password: String) async throws {
        let req = SignUpRequest(username: username, password: password)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-up",
                                                       method: "POST",
                                                       body: req)
        try Keychain.set(response.token, forKey: Self.tokenKey)
    }

    func signOut() {
        Keychain.delete(Self.tokenKey)
    }

    /// True when a token is stored and hasn't expired yet; an expired token is removed.
    var isSignedIn: Bool {
        guard let token else { return false }
        if let expiry = JWT.expirationDate(of: token), expiry <= Date() {
            signOut()
            return false
        }
        return true
    }
}
