import Foundation
import OSLog
import UIKit
import UserNotifications

/// Push notifications on this phone: asks for permission once, gives the phone's token to the server for
/// whoever is signed in, and turns a tapped notification into a place in the app (`pendingRoute`).
@MainActor
final class PushManager: ObservableObject {
    static let shared = PushManager()

    /// Whether Lizma may show notifications; the user can change it in Settings at any time.
    @Published private(set) var authorization: UNAuthorizationStatus = .notDetermined
    /// Where a tapped notification leads; MainTabs goes there and clears it.
    @Published var pendingRoute: PushRoute?

    private nonisolated static let tokenKey = "lizma.apnsDeviceToken"
    private let log = Logger(subsystem: "maxos-industry.lizma", category: "push")

    private init() {}

    /// The token iOS gave this install; signing out tells the server to forget it.
    nonisolated static var deviceToken: String? {
        UserDefaults.standard.string(forKey: tokenKey)
    }

    /// After signing in, and at every launch while signed in: asks once, then registers with APNs, which
    /// answers with the token (`didRegister`).
    func start() async {
        await refreshAuthorization()
        switch authorization {
        case .notDetermined:
            await requestPermission()
        case .authorized, .provisional, .ephemeral:
            UIApplication.shared.registerForRemoteNotifications()
        default:
            break // denied: Profile → Notifications explains how to turn them on
        }
    }

    /// The system's "Allow notifications?" (shown only once; after that only Settings can change it).
    func requestPermission() async {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
        await refreshAuthorization()
        if granted {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func refreshAuthorization() async {
        authorization = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    func didRegister(deviceToken data: Data) {
        let token = data.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(token, forKey: Self.tokenKey)
        guard AuthService.shared.isSignedIn else { return }
        Task {
            do {
                try await NotificationSettingsService.shared.registerDevice(token: token)
            } catch {
                log.error("Couldn't register the phone for notifications: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func didFailToRegister(_ error: Error) {
        // Without the Push Notifications capability (it needs the paid Apple Developer account) iOS ends here.
        log.notice("No push token from iOS: \(error.localizedDescription, privacy: .public)")
    }
}

/// The app's delegate for what only UIKit reports: the push token and notifications arriving or tapped.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        MainActor.assumeIsolated { PushManager.shared.didRegister(deviceToken: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        MainActor.assumeIsolated { PushManager.shared.didFailToRegister(error) }
    }

    /// While Lizma is open a notification still shows as a banner; news about friends refreshes the requests.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        if PushRoute(userInfo: notification.request.content.userInfo)?.changesFriends == true {
            await MainActor.run { NotificationCenter.default.post(name: .friendsChanged, object: nil) }
        }
        return [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let route = PushRoute(userInfo: response.notification.request.content.userInfo) else { return }
        await MainActor.run { PushManager.shared.pendingRoute = route }
    }
}
