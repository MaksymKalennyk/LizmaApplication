import Foundation

/// Turns the server's ISO-8601 timestamps into short relative times ("3 min ago").
enum RelativeTime {
    // Formatters are expensive to create; one set is shared by every row.
    private static let isoWithFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    private static let isoPlain = ISO8601DateFormatter()
    private static let relative: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()

    static func date(from string: String) -> Date? {
        isoWithFraction.date(from: string) ?? isoPlain.date(from: string)
    }

    /// "just now", "5 min ago", "2 days ago"; the raw string if it isn't a date.
    static func string(from dateString: String, relativeTo now: Date = Date()) -> String {
        guard let date = date(from: dateString) else { return dateString }
        // The formatter turns a few seconds (or a tiny clock skew) into "in 0 sec".
        if abs(date.timeIntervalSince(now)) < 60 {
            return String(localized: "just now", bundle: .app)
        }
        relative.locale = LanguageSettings.locale(for: LanguageSettings.shared.code) // Profile → Language
        return relative.localizedString(for: date, relativeTo: now)
    }
}
