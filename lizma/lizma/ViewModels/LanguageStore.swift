import Foundation
import SwiftUI

/// The language choice for SwiftUI: changing it rebuilds every screen in the new language (see `lizmaApp`).
@MainActor
final class LanguageStore: ObservableObject {
    static let shared = LanguageStore()

    @Published private(set) var choice: AppLanguage
    /// "en" or "uk", what the app speaks now.
    @Published private(set) var code: String

    private let settings: LanguageSettings
    /// After a change the rebuilt app opens on the Profile tab, where the change was made.
    private var opensProfile = false

    init(settings: LanguageSettings = .shared) {
        self.settings = settings
        settings.rememberPhoneLanguages()
        choice = settings.choice
        code = settings.code
    }

    var locale: Locale { LanguageSettings.locale(for: code) }

    func choose(_ language: AppLanguage) {
        guard language != choice else { return }
        settings.choose(language)
        choice = settings.choice
        let newCode = settings.code
        guard newCode != code else { return } // e.g. English chosen while the phone is in English anyway
        // Fetched in the old language: the legal pages' links carry it.
        LegalInfoStore.shared.reset()
        WidgetBridge.languageChanged(to: newCode)
        opensProfile = true
        code = newCode
    }

    /// The tab MainTabs starts on: Profile right after a language change, the feed otherwise.
    func takeStartTab() -> Int {
        defer { opensProfile = false }
        return opensProfile ? 3 : 0
    }
}
