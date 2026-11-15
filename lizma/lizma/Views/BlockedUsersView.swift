import SwiftUI

/// People the user blocked, with a way to unblock them.
struct BlockedUsersView: View {
    @State private var users: [BlockedUserDto] = []
    @State private var isLoading = false
    @State private var hasLoaded = false
    @State private var errorMessage: String? = nil
    @State private var unblockingIDs: Set<Int64> = []

    var body: some View {
        List {
            ForEach(users) { user in
                HStack(spacing: 12) {
                    AvatarView(username: user.username)

                    Text(user.username)
                        .font(.body.weight(.medium))
                        .lineLimit(1)
                        .truncationMode(.middle)

                    Spacer()

                    if unblockingIDs.contains(user.id) {
                        ProgressView()
                    } else {
                        Button("Unblock") {
                            Task { await unblock(user) }
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .accessibilityLabel("Unblock \(user.username)")
                    }
                }
            }

            if let errorMessage {
                Section {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .overlay {
            if !hasLoaded && isLoading {
                ProgressView()
            } else if hasLoaded && users.isEmpty {
                ContentUnavailableView(
                    "No blocked users",
                    systemImage: "hand.raised",
                    description: Text("People you block can't add you as a friend, and you won't see their posts, comments or reactions.")
                )
            }
        }
        .navigationTitle("Blocked users")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            users = try await SafetyService.shared.blockedUsers()
            errorMessage = nil
            hasLoaded = true
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }

    private func unblock(_ user: BlockedUserDto) async {
        guard !unblockingIDs.contains(user.id) else { return }
        unblockingIDs.insert(user.id)
        defer { unblockingIDs.remove(user.id) }
        do {
            try await SafetyService.shared.unblock(userId: user.id)
            users.removeAll { $0.id == user.id }
            errorMessage = nil
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}
