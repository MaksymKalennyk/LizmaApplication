import Foundation

/// The signed-in user's connected music providers, shared by every screen that depends on them
/// (profile row, composer, connect screen), so connecting a provider updates all of them at once.
@MainActor
final class MusicStatusStore: ObservableObject {
    static let shared = MusicStatusStore()

    @Published private(set) var status = ProvidersStatus(spotifyConnected: false, appleConnected: false)
    /// True once the status has been fetched for the current user.
    @Published private(set) var isLoaded = false
    @Published private(set) var isLoading = false
    /// Message of the last failed refresh; cleared by a successful one.
    @Published private(set) var errorMessage: String?

    private var refreshTask: Task<Void, Never>?

    private init() {}

    /// Fetches the status from the server. Concurrent callers share one request.
    func refresh() async {
        if let refreshTask {
            await refreshTask.value
            return
        }
        let task = Task { await load() }
        refreshTask = task
        await task.value
        if refreshTask == task { refreshTask = nil }
    }

    /// Fetches the status only if it isn't known yet.
    func refreshIfNeeded() async {
        guard !isLoaded else { return }
        await refresh()
    }

    /// Records a status we already know (e.g. right after connecting a provider).
    func apply(_ newStatus: ProvidersStatus) {
        status = newStatus
        isLoaded = true
        errorMessage = nil
    }

    /// Forgets everything; called on sign-out so the next user never sees the previous user's status.
    func reset() {
        refreshTask?.cancel()
        refreshTask = nil
        status = ProvidersStatus(spotifyConnected: false, appleConnected: false)
        isLoaded = false
        isLoading = false
        errorMessage = nil
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let fresh = try await MusicService.shared.status()
            guard !Task.isCancelled else { return }
            apply(fresh)
        } catch {
            guard !error.isCancellation, !Task.isCancelled else { return }
            errorMessage = error.userMessage
        }
    }
}
