import Foundation
import Combine

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var username = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var error: String? = nil
    /// A neutral message for the sign-in screen (e.g. after deleting the account).
    @Published var notice: String? = nil
    @Published var isSignedIn = AuthService.shared.isSignedIn

    private var cancellables = Set<AnyCancellable>()

    init() {
        // APIClient posts this when the server rejects our token (e.g. it expired).
        NotificationCenter.default.publisher(for: .sessionExpired)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.handleSessionExpired() }
            .store(in: &cancellables)
    }

    func signIn() async {
        let username = trimmedUsername
        await auth(action: { try await AuthService.shared.signIn(username: username, password: self.password) })
    }

    func signUp(acceptTerms: Bool) async {
        let username = trimmedUsername
        await auth(action: {
            try await AuthService.shared.signUp(username: username, password: self.password, acceptTerms: acceptTerms)
        })
    }

    /// What the user typed, without stray spaces/newlines (validation checks the same trimmed value).
    private var trimmedUsername: String {
        username.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func auth(action: @escaping () async throws -> Void) async {
        error = nil; notice = nil; isLoading = true
        do {
            try await action()
            isSignedIn = true
            password = ""
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
        isLoading = false
    }

    func signOut() {
        AuthService.shared.signOut()
        AppExtensions.signedOut()
        PreviewPlayer.shared.stop()
        ModerationStore.shared.reset()
        CurrentUserStore.shared.reset()
        resetForm()
        isSignedIn = false
    }

    /// The server deleted the account: back to the sign-in screen with a confirmation.
    func accountDeleted() {
        signOut()
        notice = String(localized: "Your account was deleted", bundle: .app)
    }

    private func handleSessionExpired() {
        guard isSignedIn else { return }
        AppExtensions.signedOut()
        PreviewPlayer.shared.stop()
        ModerationStore.shared.reset()
        CurrentUserStore.shared.reset()
        resetForm()
        isSignedIn = false
        error = String(localized: "Session expired, please sign in again", bundle: .app)
    }

    private func resetForm() {
        username = ""
        password = ""
        error = nil
        notice = nil
        isLoading = false
    }
}
