import Foundation

final class AuthService {
    static let shared = AuthService()
    private init() {}

    func signIn(username: String, password: String) async throws {
        let req = SignInRequest(username: username, password: password)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-in",
                                                       method: "POST",
                                                       body: req)
        try Keychain.set(response.token, forKey: "jwtToken")
    }

    func signUp(username: String, password: String) async throws {
        let req = SignUpRequest(username: username, password: password)
        let response = try await APIClient.shared.send(JwtAuthenticationResponse.self,
                                                       path: "/auth/sign-up",
                                                       method: "POST",
                                                       body: req)
        try Keychain.set(response.token, forKey: "jwtToken")
    }

    func signOut() {
        Keychain.delete("jwtToken")
    }

    var isSignedIn: Bool {
        (try? Keychain.get("jwtToken")) != nil
    }
}
