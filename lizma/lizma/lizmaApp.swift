import SwiftUI

@main
struct lizmaApp: App {
    /// Push notifications: the token and taps arrive through UIKit's app delegate.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            LanguageRoot()
        }
    }
}

/// The app in the language chosen in Profile → Language. A change rebuilds every screen, so text that code put
/// together (alerts, errors, times) changes along with the rest.
private struct LanguageRoot: View {
    @ObservedObject private var language = LanguageStore.shared

    var body: some View {
        RootView()
            .environment(\.locale, language.locale)
            .id(language.code)
    }
}
