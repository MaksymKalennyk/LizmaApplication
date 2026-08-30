import Foundation

@MainActor
final class FriendsViewModel: ObservableObject {
    @Published var friends: [FriendshipDto] = []
    @Published var loading = false
    @Published var error: String? = nil

    func reload() async {
        loading = true; error = nil
        do {
            friends = try await FriendService.shared.listFriends()
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
        loading = false
    }
}
