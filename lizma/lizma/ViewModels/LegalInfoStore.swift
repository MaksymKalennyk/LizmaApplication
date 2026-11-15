import Foundation

/// Terms, privacy policy and support contact from `GET /legal`, loaded once and shared.
/// If the request fails, the pages are still reachable through the fallback URLs.
@MainActor
final class LegalInfoStore: ObservableObject {
    static let shared = LegalInfoStore()

    @Published private(set) var info: LegalInfo?
    private var loadTask: Task<Void, Never>?

    private init() {}

    var termsURL: URL? { webURL(info?.termsUrl, fallbackPath: "/terms") }
    var privacyURL: URL? { webURL(info?.privacyPolicyUrl, fallbackPath: "/privacy") }

    var supportMailURL: URL? {
        guard let email = info?.contactEmail?.trimmingCharacters(in: .whitespacesAndNewlines),
              !email.isEmpty
        else { return nil }
        return URL(string: "mailto:\(email)")
    }

    func loadIfNeeded() async {
        guard info == nil else { return }
        if let loadTask {
            await loadTask.value
            return
        }
        let task = Task {
            // On failure the fallback URLs are used; the next screen that needs the info tries again.
            if let loaded = try? await SafetyService.shared.legalInfo() {
                info = loaded
            }
        }
        loadTask = task
        await task.value
        loadTask = nil
    }

    /// SFSafariViewController only accepts http(s) URLs (anything else crashes), so others fall back too.
    private func webURL(_ string: String?, fallbackPath: String) -> URL? {
        if let string, let url = URL(string: string),
           let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" {
            return url
        }
        return URL(string: AppConfig.apiBaseURL + fallbackPath)
    }
}
