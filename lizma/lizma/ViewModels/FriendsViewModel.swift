import Foundation

@MainActor
final class FriendsViewModel: ObservableObject {
    @Published var friends: [FriendshipDto] = []
    @Published var loading = false
    /// True after the first successful load (later reloads keep the list on screen).
    @Published private(set) var hasLoaded = false
    @Published var error: String? = nil

    func reload() async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        do {
            friends = try await FriendService.shared.listFriends()
            error = nil
            hasLoaded = true
        } catch {
            guard !error.isCancellation else { return }
            self.error = error.userMessage
        }
    }
}
