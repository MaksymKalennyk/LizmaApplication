import Foundation

/// The server's side of push notifications: this phone's token and what the user wants to hear about.
final class NotificationSettingsService {
    static let shared = NotificationSettingsService()
    private init() {}

    /// Development builds get tokens from the APNs sandbox.
    static var environment: String {
        #if DEBUG
        "SANDBOX"
        #else
        "PRODUCTION"
        #endif
    }

    func registerDevice(token: String) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/user/me/devices", method: "PUT",
                                            body: DeviceRegistration(token: token, environment: Self.environment))
    }

    func settings() async throws -> NotificationSettings {
        try await APIClient.shared.send(NotificationSettings.self, path: "/user/me/notifications")
    }

    func update(_ settings: NotificationSettings) async throws -> NotificationSettings {
        try await APIClient.shared.send(NotificationSettings.self, path: "/user/me/notifications", method: "PUT", body: settings)
    }
}
