import SwiftUI
import AuthenticationServices
import StoreKit

@MainActor
final class MusicConnectViewModel: ObservableObject {

    @Published var status = ProvidersStatus(spotifyConnected: false, appleConnected: false)
    @Published var errorMessage: String?

    private var webAuthSession: ASWebAuthenticationSession?
    private let anchorProvider = WebAuthAnchorProvider()

    func reloadStatus() async {
        do {
            status = try await MusicService.shared.status()
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    func connectSpotify() {
        Task { @MainActor in
            do {
                let authURL = try await MusicService.shared.spotifyAuthorizeURL()
                let scheme = URL(string: AppConfig.spotifyRedirectUri)?.scheme ?? "lizma"

                let session = ASWebAuthenticationSession(
                    url: authURL,
                    callbackURLScheme: scheme
                ) { [weak self] callbackURL, error in
                    Task { @MainActor in
                        guard let self else { return }
                        if let error {
                            self.errorMessage = error.localizedDescription
                            self.webAuthSession = nil
                            return
                        }
                        guard let callbackURL else {
                            self.errorMessage = "Missing callback URL"
                            self.webAuthSession = nil
                            return
                        }
                        do {
                            try await MusicService.shared.handleSpotifyCallback(url: callbackURL)
                            self.status.spotifyConnected = true
                        } catch {
                            self.errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                        }
                        self.webAuthSession = nil
                    }
                }

                session.presentationContextProvider = anchorProvider
                session.prefersEphemeralWebBrowserSession = true
                self.webAuthSession = session
                _ = session.start()

            } catch {
                self.errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
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
