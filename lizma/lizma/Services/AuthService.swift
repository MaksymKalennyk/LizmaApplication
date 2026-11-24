import Foundation

/// The session: a short-lived access token (JWT) for requests and a refresh token that renews it, both in
/// the Keychain. The access token is renewed shortly before it expires (or after the server rejected it),
/// so the user stays signed in as long as they use the app at least once a month.
final class AuthService {
    static let shared = AuthService()

    private static let tokenKey = "jwtToken"
    private static let refreshTokenKey = "refreshToken"
    private static let installMarkerKey = "lizma.hasLaunchedBefore"
    /// Renew this long before the access token runs out, so a request never leaves with a token that
    /// expires on the way.
    private static let renewalMargin: TimeInterval = 60

    private init() {
        // Keychain items survive deleting the app: a fresh install must not inherit the previous install's session.
        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: Self.installMarkerKey) {
            Keychain.delete(Self.tokenKey)
            Keychain.delete(Self.refreshTokenKey)
            defaults.set(true, forKey: Self.installMarkerKey)
        }
    }

    /// The stored access token, if any (it may have expired; see `validAccessToken()`).
    var token: String? { try? Keychain.get(Self.tokenKey) }

    private var refreshToken: String? { try? Keychain.get(Self.refreshTokenKey) }

    /// The current refresh token: the server recognises this phone's sign-in by it (the widget's key belongs to it).
    var currentRefreshToken: String? { refreshToken }

    /// Username of the signed-in user, read from the token.
    var currentUsername: String? { token.flatMap(JWT.username(from:)) }

    func signIn(username: String, password: String) async throws {
        let req = SignInRequest(username: username, password: password)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-in",
                                                       method: "POST",
                                                       body: req)
        try store(response)
    }

    func signUp(username: String, password: String, acceptTerms: Bool) async throws {
        let req = SignUpRequest(username: username, password: password, acceptTerms: acceptTerms)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-up",
                                                       method: "POST",
                                                       body: req)
        try store(response)
    }

    /// Permanently deletes the account (posts, comments, reactions, friends, music connections).
    /// A wrong password is a 400 with the server's message, so it never triggers the session-expired sign-out.
    func deleteAccount(password: String) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self,
                                            path: "/user/me/delete",
                                            method: "POST",
                                            body: DeleteAccountRequest(password: password))
        signOut()
    }

    /// Forgets the session here and ends it on the server (in the background: signing out never waits
    /// for the network).
    func signOut() {
        if let refreshToken {
            let deviceToken = PushManager.deviceToken
            Task.detached {
                _ = try? await APIClient.shared.send(MessageResponse.self, path: "/auth/logout", method: "POST",
                                                     body: LogoutRequest(refreshToken: refreshToken, deviceToken: deviceToken))
            }
        }
        Keychain.delete(Self.tokenKey)
        Keychain.delete(Self.refreshTokenKey)
    }

    /// True while there is a session: a refresh token, or an access token that hasn't expired yet.
    var isSignedIn: Bool {
        if refreshToken != nil { return true }
        guard let token else { return false }
        if let expiry = JWT.expirationDate(of: token), expiry <= Date() {
            signOut()
            return false
        }
        return true
    }

    // MARK: - Renewal

    /// An access token that is good for at least another minute, renewed first if needed. Nil when
    /// there is no session, or it couldn't be renewed (the request then goes out without a token or
    /// with the old one, and a 401 takes over).
    func validAccessToken() async -> String? {
        guard let token else { return nil }
        if let expiry = JWT.expirationDate(of: token), expiry.timeIntervalSinceNow < Self.renewalMargin,
           refreshToken != nil {
            if case .renewed(let fresh) = await SessionRenewal.shared.renew() {
                return fresh
            }
        }
        return self.token
    }

    /// Trades the refresh token for a new pair. Called only by `SessionRenewal`, one at a time: a refresh
    /// token works once, so two parallel renewals would end the session.
    fileprivate func renewNow() async -> SessionRenewal.Outcome {
        guard let refreshToken else { return .ended }
        do {
            let response = try await APIClient.shared.send(JwtAuthenticationResponse.self, path: "/auth/refresh",
                                                           method: "POST", body: RefreshRequest(refreshToken: refreshToken))
            try store(response)
            return .renewed(response.token)
        } catch APIError.server(let code, _) where code == 401 {
            return .ended
        } catch {
            return .failed
        }
    }

    private func store(_ response: JwtAuthenticationResponse) throws {
        try Keychain.set(response.token, forKey: Self.tokenKey)
        if let refreshToken = response.refreshToken {
            try Keychain.set(refreshToken, forKey: Self.refreshTokenKey)
        }
    }
}

/// Makes sure only one renewal runs at a time; everyone who needs a fresh token waits for that one.
actor SessionRenewal {
    static let shared = SessionRenewal()

    enum Outcome {
        /// A new access token.
        case renewed(String)
        /// The server ended the session (signed out elsewhere, suspended, too old): sign in again.
        case ended
        /// No answer (offline, server down): the session may still be fine.
        case failed
    }

    private var running: Task<Outcome, Never>?

    func renew() async -> Outcome {
        if let running {
            return await running.value
        }
        let task = Task { await AuthService.shared.renewNow() }
        running = task
        let outcome = await task.value
        running = nil
        return outcome
    }
}
