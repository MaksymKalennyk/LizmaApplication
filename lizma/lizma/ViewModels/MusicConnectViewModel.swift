import SwiftUI
import AuthenticationServices
import StoreKit

@MainActor
final class MusicConnectViewModel: ObservableObject {

    /// The app-wide provider status (see `MusicStatusStore`); writing it updates every screen.
    var status: ProvidersStatus {
        get { MusicStatusStore.shared.status }
        set { MusicStatusStore.shared.apply(newValue) }
    }
    @Published var errorMessage: String?
    /// True from tapping "Connect" until the Spotify sign-in sheet has finished.
    @Published private(set) var isConnectingSpotify = false

    private var webAuthSession: ASWebAuthenticationSession?
    private let anchorProvider = WebAuthAnchorProvider()

    func connectSpotify() {
        guard !isConnectingSpotify else { return }
        isConnectingSpotify = true
        errorMessage = nil
        Task { @MainActor in
            do {
                let authURL = try await MusicService.shared.spotifyAuthorizeURL()
                let scheme = URL(string: AppConfig.spotifyRedirectUri)?.scheme ?? "lizma"

                let session = ASWebAuthenticationSession(
                    url: authURL,
                    callbackURLScheme: scheme
                ) { [weak self] callbackURL, error in
                    Task { @MainActor in
                        await self?.finishSpotifySignIn(callbackURL: callbackURL, error: error)
                    }
                }

                session.presentationContextProvider = anchorProvider
                session.prefersEphemeralWebBrowserSession = true
                self.webAuthSession = session
                if !session.start() {
                    self.webAuthSession = nil
                    self.isConnectingSpotify = false
                    self.errorMessage = "Couldn't open the Spotify sign-in page."
                }
            } catch {
                self.isConnectingSpotify = false
                self.errorMessage = error.userMessage
            }
        }
    }

    private func finishSpotifySignIn(callbackURL: URL?, error: Error?) async {
        defer {
            webAuthSession = nil
            isConnectingSpotify = false
        }
        if let error {
            // Closing the sign-in sheet is the user's choice, not an error.
            if let authError = error as? ASWebAuthenticationSessionError, authError.code == .canceledLogin {
                return
            }
            errorMessage = error.localizedDescription
            return
        }
        guard let callbackURL else {
            errorMessage = "Missing callback URL"
            return
        }
        do {
            try await MusicService.shared.handleSpotifyCallback(url: callbackURL)
            errorMessage = nil
            status.spotifyConnected = true
            await MusicStatusStore.shared.refresh()
        } catch {
            errorMessage = error.userMessage
        }
    }

    func connectAppleMusic() {
        Task { @MainActor in
            do {
                let status = SKCloudServiceController.authorizationStatus()
                if status == .notDetermined {
                    let granted = await SKCloudServiceController.requestAuthorization()
                    guard granted == .authorized else {
                        throw NSError(domain: "Music", code: 1, userInfo: [NSLocalizedDescriptionKey: "Apple Music access denied"])
                    }
                } else if status != .authorized {
                    throw NSError(domain: "Music", code: 2, userInfo: [NSLocalizedDescriptionKey: "Apple Music not authorized"])
                }

                let devToken = try await MusicService.shared.appleDeveloperToken()
                let controller = SKCloudServiceController()
                controller.requestUserToken(forDeveloperToken: devToken) { [weak self] token, err in
                    Task { @MainActor in
                        guard let self else { return }
                        if let err {
                            self.errorMessage = err.localizedDescription
                            return
                        }
                        guard let token else {
                            self.errorMessage = "Apple Music user token not received"
                            return
                        }
                        do {
                            try await MusicService.shared.connectAppleMusic(userToken: token)
                            self.status.appleConnected = true
                        } catch {
                            self.errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                        }
                    }
                }

            } catch {
                self.errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
