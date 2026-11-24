import Foundation

/// The language Lizma speaks: the phone's own, or English or Ukrainian chosen in Profile → Language.
enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system
    case english = "en"
    case ukrainian = "uk"

    var id: String { rawValue }

    /// The language's own name, the same in every language ("English", "Українська").
    var endonym: String? {
        switch self {
        case .system: return nil
        case .english: return "English"
        case .ukrainian: return "Українська"
        }
    }

    static func endonym(of code: String) -> String {
        AppLanguage(rawValue: code)?.endonym ?? code
    }
}

/// Where the choice is kept and what follows from it.
///
/// The choice *is* the app's own `AppleLanguages` setting, the one Settings → Lizma → Language changes: both places
/// always agree, and the phone's dialogs and push notifications follow it from the next launch. Lizma itself switches
/// at once (`LanguageStore`): its texts are looked up in the chosen language's strings (`Bundle.app`).
struct LanguageSettings {
    static let shared = LanguageSettings()

    /// The languages Lizma has (its String Catalog).
    static let available = ["en", "uk"]

    var defaults: UserDefaults = .standard
    /// The preferences domain the override lives in: the app's own.
    var domain: String = Bundle.main.bundleIdentifier ?? "maxos-industry.lizma"
    /// The phone's languages in order, without the app's override. Apps can't read the phone's setting itself, and
    /// `Locale.preferredLanguages` starts with the override once there is one, so they're remembered at a launch
    /// without one (`rememberPhoneLanguages`).
    var systemLanguages: () -> [String] = {
        UserDefaults.standard.stringArray(forKey: LanguageSettings.phoneLanguagesKey) ?? Locale.preferredLanguages
    }

    private static let overrideKey = "AppleLanguages"
    private static let phoneLanguagesKey = "lizma.phoneLanguages"

    /// At launch: while no language is chosen, iOS's preferred languages are the phone's own, so they're kept for
    /// the times a choice hides them.
    func rememberPhoneLanguages(_ current: [String] = Locale.preferredLanguages) {
        guard choice == .system, !current.isEmpty else { return }
        defaults.set(current, forKey: Self.phoneLanguagesKey)
    }

    var choice: AppLanguage {
        let override = (defaults.persistentDomain(forName: domain)?[Self.overrideKey] as? [String])?.first
        switch override.map({ String($0.prefix(2)) }) {
        case "en": return .english
        case "uk": return .ukrainian
        default: return .system
        }
    }

    /// "en" or "uk": the chosen language, or the phone's closest match among Lizma's.
    var code: String {
        switch choice {
        case .english: return "en"
        case .ukrainian: return "uk"
        case .system: return systemCode
        }
    }

    /// The phone's closest match among Lizma's languages, whatever was chosen here.
    var systemCode: String {
        Bundle.preferredLocalizations(from: Self.available, forPreferences: systemLanguages()).first ?? "en"
    }

    func choose(_ language: AppLanguage) {
        if language == .system {
            defaults.removeObject(forKey: Self.overrideKey)
        } else {
            defaults.set([language.rawValue], forKey: Self.overrideKey)
        }
    }

    /// The strings of `code` (its `.lproj` in the app); a key that isn't translated reads as the key, i.e. English.
    static func bundle(for code: String) -> Bundle {
        Bundle.main.path(forResource: code, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
    }

    /// The language with the phone's region, for dates and numbers.
    static func locale(for code: String) -> Locale {
        if let region = Locale.current.region?.identifier {
            return Locale(identifier: "\(code)_\(region)")
        }
        return Locale(identifier: code)
    }
}

extension Bundle {
    /// The app's strings in the language chosen in Profile → Language: pass it to `String(localized:bundle:)`.
    static var app: Bundle { LanguageSettings.bundle(for: LanguageSettings.shared.code) }
}
