import SwiftUI

struct FriendsScreen: View {
    @StateObject private var vm = FriendsViewModel()
    /// Shared with the tab bar, which shows the number of incoming requests.
    @EnvironmentObject private var requests: RequestsViewModel

    @State private var query: String = ""
    @State private var results: [UserDto] = []
    @State private var searching = false
    /// One line at the top of the list: a hint (secondary) or an error (red).
    @State private var notice: Notice? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var selfUsername: String? = nil

    @State private var sendingIDs: Set<Int64> = []
    @State private var addedUserIDs: Set<Int64> = []

    @State private var generatingLink = false
    @State private var sharePayload: SharePayload? = nil
    @State private var reportTarget: ReportTarget? = nil
    @State private var blockTarget: BlockTarget? = nil

    private var friendIDs: Set<Int64> {
        Set(vm.friends.map { $0.friendId })
    }
    private var incomingFromIDs: Set<Int64> {
        Set(requests.received.map { $0.requesterId })
    }
    private var outgoingToIDs: Set<Int64> {
        Set(requests.sent.map { $0.recipientId })
    }
    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            List {
                if let notice {
                    Section {
                        Label(notice.text, systemImage: notice.isError ? "exclamationmark.triangle" : "info.circle")
                            .font(.footnote)
                            .foregroundStyle(notice.isError ? Color.red : Color.secondary)
                    }
                }

                if trimmedQuery.isEmpty {
                    incomingRequestsSection
                    inviteSection
                    friendsSection
                    sentRequestsSection
                } else {
                    searchSection
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Friends")
            .searchable(text: $query,
                        placement: .navigationBarDrawer(displayMode: .always),
                        prompt: "Search by username")
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .onChange(of: query) { _, newValue in
                scheduleSearch(for: newValue)
            }
            .refreshable { await reloadAll() }
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheet(activityItems: payload.items)
        }
        .sheet(item: $reportTarget) { target in
            ReportSheet(target: target)
        }
        // Blocking ends the friendship; `.friendsChanged` (posted after the block) reloads the list.
        .blockConfirmation(target: $blockTarget)
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

    /// Requests waiting for an answer come first: they are the one thing here that needs the user.
    @ViewBuilder
    private var incomingRequestsSection: some View {
        if !requests.received.isEmpty {
            Section("Friend requests") {
                ForEach(requests.received) { request in
                    IncomingRequestRow(request: request,
                                       isBusy: requests.inFlight.contains(request.id),
                                       accept: { Task { await requests.accept(request) } },
                                       decline: { Task { await requests.reject(request) } })
                        // A declined request can be sent again, so an unwanted sender can also be blocked.
                        .modifier(SafetyActions(report: { report(userId: request.requesterId) },
                                                block: { block(userId: request.requesterId,
                                                               username: request.requesterUsername) }))
                }
            }
        }
    }

    @ViewBuilder
    private var sentRequestsSection: some View {
        if !requests.sent.isEmpty {
            Section("Sent requests") {
                ForEach(requests.sent) { request in
                    SentRequestRow(request: request)
                }
            }
        }
    }

    /// Invite links are single-use and short-lived, so every tap creates a new one.
    private var inviteSection: some View {
        Section {
            Button {
                createInviteLink()
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "link")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(.tint.opacity(0.15)))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Share invite link")
                            .font(.body.weight(.semibold))
                        // `Color.secondary`: inside a button, `.secondary` is a paler tint.
                        Text("Works once and expires in 7 days")
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }

                    Spacer(minLength: 8)

                    if generatingLink {
                        ProgressView()
                    }
                }
            }
            .disabled(generatingLink)
        }
    }

    @ViewBuilder
    private var friendsSection: some View {
        if vm.loading && !vm.hasLoaded {
            placeholderRow { ProgressView() }
        } else if let err = vm.error, vm.friends.isEmpty {
            placeholderRow {
                ContentUnavailableView(
                    "Couldn't load friends",
                    systemImage: "exclamationmark.triangle",
                    description: Text(err)
                )
            }
        } else if vm.friends.isEmpty {
            placeholderRow {
                ContentUnavailableView(
                    "No friends yet",
                    systemImage: "person.2",
                    description: Text("Search by username or share an invite link.")
                )
            }
        } else {
            Section(vm.friends.count == 1 ? "1 friend" : "\(vm.friends.count) friends") {
                ForEach(vm.friends) { friend in
                    HStack(spacing: 12) {
                        AvatarView(username: friend.friendUsername)
                        Text(friend.friendUsername)
                            .font(.body.weight(.medium))
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                    .modifier(SafetyActions(report: { report(userId: friend.friendId) },
                                            block: { block(userId: friend.friendId,
                                                           username: friend.friendUsername) }))
                }
            }
        }
    }

    @ViewBuilder
    private var searchSection: some View {
        if results.isEmpty {
            placeholderRow {
                if searching {
                    ProgressView()
                } else {
                    ContentUnavailableView.search(text: trimmedQuery)
                }
            }
        } else {
            Section {
                ForEach(results) { user in
                    userRow(user)
                }
            }
        }
    }

    /// A row without the card background, for spinners and empty states inside the list.
    private func placeholderRow<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        Section {
            content()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .listRowBackground(Color.clear)
        }
    }

    private func userRow(_ user: UserDto) -> some View {
        HStack(spacing: 12) {
            AvatarView(username: user.username)

            Text(user.username)
                .font(.body.weight(.medium))
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: 8)

            relationControl(for: user)
        }
    }

    /// What the user can do with a search result, said in words rather than with an icon.
    @ViewBuilder
    private func relationControl(for user: UserDto) -> some View {
        if friendIDs.contains(user.id) {
            // Not a `Label`: in a list row that would pull the separator over to its text.
            HStack(spacing: 4) {
                Image(systemName: "checkmark")
                Text("Friends")
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .accessibilityElement(children: .combine)
        } else if outgoingToIDs.contains(user.id) || addedUserIDs.contains(user.id) {
            Text("Requested")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } else if sendingIDs.contains(user.id) {
            ProgressView().controlSize(.small)
        } else if incomingFromIDs.contains(user.id) {
            // They asked first: sending a request back makes you friends right away.
            Button("Accept") {
                sendRequest(to: user)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
            .accessibilityLabel("Accept \(user.username)'s friend request")
        } else {
            Button {
                sendRequest(to: user)
            } label: {
                Label("Add", systemImage: "plus")
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .accessibilityLabel("Add \(user.username)")
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
        await requests.reload()
        if let error = requests.error {
            notice = .error(error)
        }
    }

    private func report(userId: Int64) {
        reportTarget = ReportTarget(type: .user, targetId: userId)
    }

    private func block(userId: Int64, username: String) {
        blockTarget = BlockTarget(userId: userId, username: username)
    }
}

/// Report and block for a row with another user: in the long-press menu and as swipe actions.
private struct SafetyActions: ViewModifier {
    let report: () -> Void
    let block: () -> Void

    func body(content: Content) -> some View {
        content
            .contextMenu {
                Button(action: report) {
                    Label("Report user", systemImage: "exclamationmark.bubble")
                }
                Button(role: .destructive, action: block) {
                    Label("Block user", systemImage: "hand.raised")
                }
            }
            // Not `.destructive`: that role animates the row away before the user confirms.
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(action: block) {
                    Label("Block", systemImage: "hand.raised")
                }
                .tint(.red)
                Button(action: report) {
                    Label("Report", systemImage: "exclamationmark.bubble")
                }
                .tint(.orange)
            }
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
