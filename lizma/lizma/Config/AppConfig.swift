import Foundation

enum AppConfig {
    static let apiBaseURL: String = "http://localhost:8080"

    static let spotifyClientId = "886cea2ad68346a597acede21adaf379"
    static let spotifyRedirectUri = "lizma://oauth-callback/spotify"
    static let spotifyScopes = [
        "user-read-currently-playing",
        "user-read-playback-state"
    ]
}
