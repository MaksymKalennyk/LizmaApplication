import Foundation

/// Whether the signed-in user moderates, and how much waits for them. Shared by Profile (the badge on
/// "Moderation") and the moderation screens, which refresh it after every decision.
@MainActor
final class ModerationStore: ObservableObject {
    static let shared = ModerationStore()

    @Published private(set) var isModerator = false
    @Published private(set) var summary: AdminSummary?

    private var refreshTask: Task<Void, Never>?

    private init() {}

    /// Asks the server again; concurrent callers share one request. Failures keep what was known.
    func refresh() async {
        if let refreshTask {
            await refreshTask.value
            return
        }
        let task = Task {
            guard let me = try? await ModerationService.shared.me() else { return }
            isModerator = me.admin
            summary = me.admin ? (try? await ModerationService.shared.summary()) ?? summary : nil
        }
        refreshTask = task
        await task.value
        refreshTask = nil
    }

    /// Signing out: the next user starts from nothing.
    func reset() {
        refreshTask?.cancel()
        refreshTask = nil
        isModerator = false
        summary = nil
    }
}
