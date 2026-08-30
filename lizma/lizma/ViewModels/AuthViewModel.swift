import Foundation
import Combine

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var username = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var error: String? = nil
    @Published var isSignedIn = AuthService.shared.isSignedIn

    func signIn() async {
        await auth(action: { try await AuthService.shared.signIn(username: self.username, password: self.password) })
    }

    func signUp() async {
        await auth(action: { try await AuthService.shared.signUp(username: self.username, password: self.password) })
    }

    private func auth(action: @escaping () async throws -> Void) async {
        error = nil; isLoading = true
        do {
            try await action()
            isSignedIn = true
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
        isLoading = false
    }

    func signOut() {
        AuthService.shared.signOut()
        isSignedIn = false
    }
}
