import SwiftUI

/// Anyone's profile: photo, name, how you're connected, and their posts (for friends and yourself).
/// Your own is the same page with "Edit profile" and your posts.
struct UserProfileScreen: View {
    let username: String

    @State private var profile: ProfileResponse?
    @State private var posts: [PostResponse] = []
    @State private var hasMorePosts = true
    @State private var isLoadingPosts = false
    @State private var loadError: String?
    @State private var actionError: String?
    @State private var isWorking = false

    @State private var openedPost: OpenedPost?
    @State private var isEditing = false
    @State private var isConfirmingRemove = false
    @State private var reportTarget: ReportTarget?
    @State private var blockTarget: BlockTarget?

    @Environment(\.dismiss) private var dismiss

    private static let pageSize = 30
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 3)

    var body: some View {
        ScrollView {
            if let profile {
                VStack(spacing: 24) {
                    header(profile)
                    postsSection(profile)
                }
                .padding(.top, 12)
                .padding(.bottom, 32)
            } else if let loadError {
                ContentUnavailableView {
                    Label("Couldn't load the profile", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(loadError)
                } actions: {
                    Button("Retry") { Task { await load() } }
                        .buttonStyle(.bordered)
                }
                .padding(.top, 80)
            } else {
                ProgressView()
                    .padding(.top, 120)
            }
        }
        .navigationTitle(profile?.name ?? username)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let profile, profile.relationship != .selfProfile {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            reportTarget = ReportTarget(type: .user, targetId: profile.id)
                        } label: {
                            Label("Report user", systemImage: "exclamationmark.bubble")
                        }
                        Button(role: .destructive) {
                            blockTarget = BlockTarget(userId: profile.id, username: profile.username)
                        } label: {
                            Label("Block user", systemImage: "hand.raised")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .accessibilityLabel("More options")
                }
            }
        }
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $isEditing) {
            if let profile {
                EditProfileView(profile: profile) { updated in
                    self.profile = updated
                    // The author name on the posts below changes too.
                    Task { await reloadPosts() }
                }
            }
        }
        .sheet(item: $reportTarget) { target in
            ReportSheet(target: target)
        }
        .blockConfirmation(target: $blockTarget) { _ in
            // They're gone from your app now, this page included.
            dismiss()
        }
        .fullScreenCover(item: $openedPost) { opened in
            PostPagerView(posts: $posts, startPostId: opened.id,
                          onReachEnd: { Task { await loadMorePosts() } },
                          onAuthorBlocked: { dismiss() })
        }
    }

    // MARK: - Header

    private func header(_ profile: ProfileResponse) -> some View {
        VStack(spacing: 12) {
            AvatarView(username: profile.username, imageURL: profile.avatarUrl, size: 104)

            VStack(spacing: 2) {
                Text(profile.name)
                    .font(.title2.weight(.bold))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                if profile.displayName != nil {
                    Text("@\(profile.username)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
            .padding(.horizontal, 24)

            relationshipActions(profile)
                .disabled(isWorking)

            if let actionError {
                Text(actionError)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
        }
        .confirmationDialog("Remove from friends?", isPresented: $isConfirmingRemove, titleVisibility: .visible) {
            Button("Remove \(profile.username)", role: .destructive) {
                Task { await act { try await FriendService.shared.removeFriend(username: profile.username) } }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You and \(profile.username) will stop seeing each other's posts. They won't be notified.")
        }
    }

    @ViewBuilder
    private func relationshipActions(_ profile: ProfileResponse) -> some View {
        switch profile.relationship {
        case .selfProfile:
            Button("Edit profile") { isEditing = true }
                .buttonStyle(.bordered)
        case .friend:
            Menu {
                Button(role: .destructive) {
                    isConfirmingRemove = true
                } label: {
                    Label("Remove friend", systemImage: "person.badge.minus")
                }
            } label: {
                Label("Friends", systemImage: "checkmark")
            }
            .buttonStyle(.bordered)
        case .requestSent:
            Button("Cancel request") {
                guard let requestId = profile.requestId else { return }
                Task { await act { try await FriendService.shared.cancelRequest(requestId) } }
            }
            .buttonStyle(.bordered)
        case .requestReceived:
            HStack(spacing: 10) {
                Button("Accept") {
                    guard let requestId = profile.requestId else { return }
                    Task { await act { try await FriendService.shared.respond(to: requestId, status: .accepted) } }
                }
                .buttonStyle(.borderedProminent)
                Button("Decline") {
                    guard let requestId = profile.requestId else { return }
                    Task { await act { try await FriendService.shared.respond(to: requestId, status: .declined) } }
                }
                .buttonStyle(.bordered)
            }
        case .none:
            Button {
                Task { await act { _ = try await FriendService.shared.sendRequest(to: profile.id) } }
            } label: {
                Label("Add friend", systemImage: "person.badge.plus")
            }
            .buttonStyle(.borderedProminent)
        }
    }

    // MARK: - Posts

    @ViewBuilder
    private func postsSection(_ profile: ProfileResponse) -> some View {
        if !profile.canSeePosts {
            VStack(spacing: 8) {
                Image(systemName: "lock")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                Text("Only friends see \(profile.name)'s posts")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 24)
            .padding(.horizontal, 32)
        } else if posts.isEmpty && !isLoadingPosts {
            VStack(spacing: 8) {
                Image(systemName: "music.note")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                Text(profile.relationship == .selfProfile ? "You haven't shared anything yet" : "No posts yet")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.top, 24)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(profile.relationship == .selfProfile ? "My posts" : "Posts")
                    .font(.headline)
                    .padding(.horizontal, 16)
                LazyVGrid(columns: columns, spacing: 2) {
                    ForEach(posts) { post in
                        Button {
                            openedPost = OpenedPost(id: post.id)
                        } label: {
                            PostTile(post: post)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(post.trackName) by \(post.artistName)")
                        .onAppear {
                            if post.id == posts.last?.id { Task { await loadMorePosts() } }
                        }
                    }
                }
                if isLoadingPosts {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
            }
        }
    }

    // MARK: - Loading

    private func load() async {
        do {
            let loaded = try await ProfileService.shared.profile(username: username)
            profile = loaded
            loadError = nil
            if loaded.canSeePosts {
                await reloadPosts()
            } else {
                posts = []
            }
        } catch {
            guard !error.isCancellation else { return }
            if profile == nil { loadError = error.userMessage } else { actionError = error.userMessage }
        }
    }

    private func reloadPosts() async {
        isLoadingPosts = true
        defer { isLoadingPosts = false }
        do {
            let page = try await ProfileService.shared.posts(username: username, size: Self.pageSize)
            posts = page
            hasMorePosts = page.count == Self.pageSize
        } catch {
            guard !error.isCancellation else { return }
            actionError = error.userMessage
        }
    }

    private func loadMorePosts() async {
        guard hasMorePosts, !isLoadingPosts, let last = posts.last else { return }
        isLoadingPosts = true
        defer { isLoadingPosts = false }
        do {
            let page = try await ProfileService.shared.posts(username: username, before: last.id, size: Self.pageSize)
            posts.append(contentsOf: page.filter { new in !posts.contains { $0.id == new.id } })
            hasMorePosts = page.count == Self.pageSize
        } catch {
            guard !error.isCancellation else { return }
            actionError = error.userMessage
        }
    }

    /// A friend action, then the page and every friends list catch up.
    private func act(_ action: () async throws -> Void) async {
        isWorking = true
        actionError = nil
        defer { isWorking = false }
        do {
            try await action()
            NotificationCenter.default.post(name: .friendsChanged, object: nil)
            await load()
        } catch {
            guard !error.isCancellation else { return }
            actionError = error.userMessage
        }
    }
}

/// Which post of the grid is open in the pager.
private struct OpenedPost: Identifiable {
    let id: Int
}

/// One square of the posts grid: the cover, or the track name on a coloured square without one.
private struct PostTile: View {
    let post: PostResponse

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                if let url = post.albumCoverUrl.flatMap(URL.init(string:)) {
                    RemoteImage(url: url) { image in
                        image.resizable().scaledToFill()
                    } placeholder: { _ in
                        placeholder
                    }
                } else {
                    placeholder
                }
            }
            .clipped()
            .contentShape(Rectangle())
    }

    private var placeholder: some View {
        ZStack {
            AvatarView.color(for: post.authorUsername).opacity(0.35)
            VStack(spacing: 4) {
                Image(systemName: "music.note")
                Text(post.trackName)
                    .font(.caption2.weight(.semibold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(.white)
            .padding(6)
        }
    }
}
