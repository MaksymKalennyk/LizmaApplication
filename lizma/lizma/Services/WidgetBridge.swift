import Foundation
import OSLog
import WidgetKit

/// Keeps the home screen widget in step with the app: gives it its key after signing in, takes the key away on
/// signing out, tells it the server and the language, and asks it to refresh when there's news.
@MainActor
enum WidgetBridge {
    private static let log = Logger(subsystem: "maxos-industry.lizma", category: "widget")

    /// After signing in, and at every launch while signed in.
    static func signedIn() async {
        WidgetShared.baseURL = AppConfig.apiBaseURL
        WidgetShared.language = LanguageSettings.shared.code
        if WidgetShared.widgetKey == nil {
            await requestKey()
        }
        reload()
    }

    /// Signing out or the session ending: the widget asks to sign in again. (The server drops the key anyway: it
    /// belongs to the sign-in that just ended.)
    static func signedOut() {
        WidgetShared.widgetKey = nil
        reload()
    }

    static func languageChanged(to code: String) {
        WidgetShared.language = code
        reload()
    }

    /// Asks iOS to redraw the widget with fresh songs.
    static func reload() {
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetShared.kind)
    }

    /// The key is tied to this phone's sign-in, which the server recognises by its refresh token.
    private static func requestKey() async {
        // Renews the session first if needed, so the refresh token sent is the current one.
        guard await AuthService.shared.validAccessToken() != nil,
              let refreshToken = AuthService.shared.currentRefreshToken else { return }
        do {
            let response = try await APIClient.shared.send(WidgetKeyResponse.self, path: "/user/me/widget-key",
                                                           method: "POST", body: RefreshRequest(refreshToken: refreshToken))
            WidgetShared.widgetKey = response.key
        } catch {
            // Tried again at the next launch.
            log.notice("No widget key yet: \(error.localizedDescription, privacy: .public)")
        }
    }
}
