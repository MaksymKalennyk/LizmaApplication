import Foundation

/// The signed-in user's own name and photo, for the Profile tab; refreshed after editing the profile.
@MainActor
final class CurrentUserStore: ObservableObject {
    static let shared = CurrentUserStore()

    @Published private(set) var me: MeResponse?

    private init() {}

    func refresh() async {
        if let me = try? await ModerationService.shared.me() {
            self.me = me
        }
    }

    /// Signing out: the next user starts from nothing.
    func reset() {
        me = nil
    }
}
