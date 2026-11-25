import Foundation
import OSLog
import WidgetKit

/// Keeps the app's extensions in step with the app: the home screen widget and the share extension (posting a song
/// shared from Apple Music or Spotify). After signing in they get keys of their own from the server, tied to this
/// sign-in; signing out takes them away. The widget also learns the server and the language, and is asked to
/// refresh when there's news.
@MainActor
enum AppExtensions {
    private static let log = Logger(subsystem: "maxos-industry.lizma", category: "extensions")

    /// After signing in, and at every launch while signed in.
    static func signedIn() async {
        WidgetShared.baseURL = AppConfig.apiBaseURL
        WidgetShared.language = LanguageSettings.shared.code
        if WidgetShared.widgetKey == nil, let key = await requestKey(path: "/user/me/widget-key") {
            WidgetShared.widgetKey = key
        }
        if ShareKey.current == nil, let key = await requestKey(path: "/user/me/share-key") {
            ShareKey.current = key
        }
        reloadWidget()
    }

    /// Signing out or the session ending: the extensions ask to sign in again. (The server drops their keys anyway:
    /// they belong to the sign-in that just ended.)
    static func signedOut() {
        WidgetShared.widgetKey = nil
        ShareKey.current = nil
        reloadWidget()
    }

    static func languageChanged(to code: String) {
        WidgetShared.language = code
        reloadWidget()
    }

    /// Asks iOS to redraw the widget with fresh songs.
    static func reloadWidget() {
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetShared.kind)
    }

    /// A key tied to this phone's sign-in, which the server recognises by its refresh token.
    private static func requestKey(path: String) async -> String? {
        // Renews the session first if needed, so the refresh token sent is the current one.
        guard await AuthService.shared.validAccessToken() != nil,
              let refreshToken = AuthService.shared.currentRefreshToken else { return nil }
        do {
            return try await APIClient.shared.send(ExtensionKeyResponse.self, path: path, method: "POST",
                                                   body: RefreshRequest(refreshToken: refreshToken)).key
        } catch {
            // Tried again at the next launch.
            log.notice("No key from \(path, privacy: .public) yet: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private struct ExtensionKeyResponse: Decodable {
        let key: String
    }
}
