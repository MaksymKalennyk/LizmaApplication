import SwiftUI

struct FriendsScreen: View {
    @StateObject private var vm = FriendsViewModel()

    @State private var query: String = ""
    @State private var results: [UserDto] = []
    @State private var searching = false
    /// One line under the search field: a hint (secondary) or an error (red).
    @State private var notice: Notice? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var selfUsername: String? = nil

    @State private var received: [FriendRequestDto] = []
    @State private var sent: [FriendRequestDto] = []

    @State private var sendingIDs: Set<Int64> = []
    @State private var addedUserIDs: Set<Int64> = []

    @State private var generatingLink = false
    @State private var sharePayload: SharePayload? = nil

    private var friendIDs: Set<Int64> {
        Set(vm.friends.map { $0.friendId })
    }
    private var incomingFromIDs: Set<Int64> {
        Set(received.map { $0.requesterId })
    }
    private var outgoingToIDs: Set<Int64> {
        Set(sent.map { $0.recipientId })
    }
    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                TextField("Search by username", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(.thinMaterial))
                    .padding(.horizontal)
                    .onChange(of: query) { _, newValue in
                        scheduleSearch(for: newValue)
                    }

                if trimmedQuery.isEmpty {
                    inviteRow
                }

                if searching { ProgressView().padding(.top, 4) }
                if let notice {
                    Text(notice.text)
                        .font(.footnote)
                        .foregroundStyle(notice.isError ? Color.red : Color.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                Group {
                    if !trimmedQuery.isEmpty {
                        searchResults
                    } else {
                        friendsList
                    }
                }
            }
            .navigationTitle("Friends")
            .refreshable { await reloadAll() }
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheet(activityItems: payload.items)
        }
        .task {
            await reloadAll()
        }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            Task { await reloadAll() }
        }
        .onDisappear {
            searchTask?.cancel()
            searching = false
        }
    }

    // MARK: - Content

    /// Invite links are single-use and short-lived, so every tap creates a new one.
    private var inviteRow: some View {
        HStack(spacing: 12) {
            Button {
                createInviteLink()
            } label: {
                HStack(spacing: 6) {
                    if generatingLink {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: "link")
                    }
                    Text("Share invite link")
                }
                .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.bordered)
            .disabled(generatingLink)
            .accessibilityHint("Each link works once and expires in 7 days")

            Text("Each link works once and expires in 7 days")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityHidden(true)
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private var searchResults: some View {
        if results.isEmpty && !searching {
            ContentUnavailableView(
                "No matches",
                systemImage: "magnifyingglass",
                description: Text("Try a different username")
            )
        } else {
            List(results, id: \.id) { user in
                userRow(user)
            }
            .listStyle(.insetGrouped)
        }
    }

    @ViewBuilder
    private var friendsList: some View {
        if vm.loading && !vm.hasLoaded {
            ProgressView().controlSize(.large).frame(maxHeight: .infinity)
        } else if let err = vm.error, vm.friends.isEmpty {
            // Scrollable so pull-to-refresh works on the empty/error states too.
            ScrollView {
                ContentUnavailableView(
                    "Error",
                    systemImage: "exclamationmark.triangle",
                    description: Text(err)
                )
                .containerRelativeFrame(.vertical)
            }
        } else if vm.friends.isEmpty {
            ScrollView {
                ContentUnavailableView(
                    "No friends yet",
                    systemImage: "person.2.slash",
                    description: Text("Search above to add friends")
                )
                .containerRelativeFrame(.vertical)
            }
        } else {
            List(vm.friends) { f in
                HStack {
                    Circle()
                        .frame(width: 36, height: 36)
                        .opacity(0.1)
                        .overlay(Text(String(f.friendUsername.prefix(1))).font(.headline))

                    Text(f.friendUsername).font(.body).bold()
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func userRow(_ user: UserDto) -> some View {
        let isFriend = friendIDs.contains(user.id)
        let hasIncoming = incomingFromIDs.contains(user.id)
        let hasOutgoing = outgoingToIDs.contains(user.id) || addedUserIDs.contains(user.id)
        let isSending = sendingIDs.contains(user.id)

        return HStack(spacing: 12) {
            Circle()
                .frame(width: 32, height: 32)
                .opacity(0.1)
                .overlay(Text(String(user.username.prefix(1))).font(.subheadline))

            Text(user.username).bold()

            Spacer()

            Button {
                // Already connected: explain instead of sending a request the server would refuse.
                if isFriend {
                    notice = .info("\(user.username) is already your friend.")
                } else if hasIncoming {
                    notice = .info("\(user.username) has already sent you a request. Accept it in Profile.")
                } else if hasOutgoing {
                    notice = .info("You have already sent a friend request to \(user.username).")
                } else {
                    sendRequest(to: user)
                }
            } label: {
                if isFriend {
                    Image(systemName: "person.crop.circle.badge.checkmark").imageScale(.large)
                        .foregroundStyle(.secondary)
                } else if hasIncoming {
                    Image(systemName: "arrow.down.circle").imageScale(.large)
                        .foregroundStyle(.secondary)
                } else if hasOutgoing {
                    Image(systemName: "checkmark.circle.fill").imageScale(.large)
                        .foregroundStyle(.secondary)
                } else if isSending {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: "plus.circle.fill").imageScale(.large)
                }
            }
            .buttonStyle(.plain)
            .disabled(isSending)
            .accessibilityLabel(accessibilityLabelFor(user: user.username,
                                                      friend: isFriend,
                                                      incoming: hasIncoming,
                                                      outgoing: hasOutgoing))
        }
    }

    // MARK: - Actions

    private func scheduleSearch(for text: String) {
        searchTask?.cancel()
        notice = nil
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            results = []
            searching = false
            return
        }
        searching = true
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            do {
                let users = try await UserService.shared.searchUsers(query: trimmed, limit: 20)
                guard !Task.isCancelled else { return }
                let me = selfUsername?.lowercased()
                results = users.filter { $0.username.lowercased() != me }
            } catch {
                // A newer search replaced this one: its outcome no longer matters.
                guard !Task.isCancelled, !error.isCancellation else { return }
                notice = .error(error.userMessage)
            }
            searching = false
        }
    }

    private func sendRequest(to user: UserDto) {
        notice = nil
        Task {
            sendingIDs.insert(user.id)
            defer { sendingIDs.remove(user.id) }
            do {
                let reply = try await FriendService.shared.sendRequest(to: user.id)
                if reply.trimmingCharacters(in: .whitespacesAndNewlines) == FriendService.becameFriendsReply {
                    // They had already asked us, so the server accepted their request instead.
                    notice = .info("You and \(user.username) are now friends!")
                    NotificationCenter.default.post(name: .friendsChanged, object: nil)
                } else {
                    withAnimation { _ = addedUserIDs.insert(user.id) }
                    await loadRequests()
                }
            } catch {
                guard !error.isCancellation else { return }
                notice = .error(error.userMessage)
                await loadRequests()
            }
        }
    }

    private func createInviteLink() {
        guard !generatingLink else { return }
        generatingLink = true
        notice = nil
        Task {
            defer { generatingLink = false }
            do {
                let invite = try await FriendService.shared.createInvite()
                guard let url = invite.shareURL else {
                    notice = .error("Couldn't create an invite link. Please try again.")
                    return
                }
                sharePayload = SharePayload(items: [url])
            } catch {
                guard !error.isCancellation else { return }
                notice = .error(error.userMessage)
            }
        }
    }

    private func reloadAll() async {
        selfUsername = AuthService.shared.currentUsername
        await vm.reload()
        await loadRequests()
    }

    private func loadRequests() async {
        do {
            async let rcv = FriendService.shared.listReceivedRequests()
            async let snt = FriendService.shared.listSentRequests()
            (received, sent) = try await (rcv, snt)
        } catch {
            guard !error.isCancellation else { return }
            notice = .error(error.userMessage)
        }
    }

    private func accessibilityLabelFor(user: String, friend: Bool, incoming: Bool, outgoing: Bool) -> String {
        if friend { return "\(user) is already your friend" }
        if incoming { return "\(user) has already sent you a request" }
        if outgoing { return "Request already sent to \(user)" }
        return "Add \(user)"
    }
}

private struct Notice: Equatable {
    let text: String
    let isError: Bool

    static func info(_ text: String) -> Notice { Notice(text: text, isError: false) }
    static func error(_ text: String) -> Notice { Notice(text: text, isError: true) }
}

private struct SharePayload: Identifiable {
    let id = UUID()
    let items: [Any]
}
