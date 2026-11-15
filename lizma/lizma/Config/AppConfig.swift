import Foundation

enum AppConfig {
    static let apiBaseURL: String = {
        #if DEBUG
        // Debug builds can be pointed at another backend (a LAN address for a real device, a test
        // instance) without editing the code: set LIZMA_API_BASE_URL in the scheme's environment,
        // or SIMCTL_CHILD_LIZMA_API_BASE_URL when launching with `xcrun simctl launch`.
        if let override = ProcessInfo.processInfo.environment["LIZMA_API_BASE_URL"], !override.isEmpty {
            return override
        }
        #endif
        return "http://localhost:8080"
    }()

    static let spotifyClientId = "886cea2ad68346a597acede21adaf379"
    /// Must be registered exactly like this in the Spotify Developer Dashboard (Redirect URIs).
    static let spotifyRedirectUri = "lizma://oauth-callback/spotify"
    /// Read-only permissions asked on "Connect Spotify". Search itself needs none.
    static let spotifyScopes = [
        "user-read-currently-playing",
        "user-read-playback-state"
    ]
}
