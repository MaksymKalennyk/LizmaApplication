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

    /// Profile photos come as paths on our server ("/avatars/…"); covers are full URLs already.
    static func mediaURL(_ path: String?) -> URL? {
        guard let path, !path.isEmpty else { return nil }
        return path.hasPrefix("/") ? URL(string: apiBaseURL + path) : URL(string: path)
    }

}
