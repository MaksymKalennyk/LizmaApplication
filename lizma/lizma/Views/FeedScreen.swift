import SwiftUI

struct FeedScreen: View {
    private let pageSize = 20

    @State private var posts: [PostResponse] = []
    /// The post on screen; keeps the reader's place when posts are added or reloaded.
    @State private var currentPostId: Int? = nil
    @State private var nextPage = 0
    @State private var hasMore = true
    @State private var hasLoadedOnce = false
    @State private var isLoading = false
    /// The single page load in flight (first page or next page).
    @State private var loadTask: Task<Void, Never>? = nil
    /// Bumped by every reload so results of superseded loads are dropped.
    @State private var loadGeneration = 0
    @State private var errorMessage: String? = nil
    @State private var reactionError: String? = nil

    @State private var playingTrackId: String? = nil

    @State private var selectedPostForReaction: PostResponse? = nil
    @State private var selectedPostForReactionList: PostResponse? = nil
    @State private var selectedPostForComments: PostResponse? = nil
    @State private var reportTarget: ReportTarget? = nil
    @State private var blockTarget: BlockTarget? = nil
    @State private var selfUsername: String? = nil

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if posts.isEmpty {
                if isLoading || (!hasLoadedOnce && errorMessage == nil) {
                    ProgressView().tint(.white)
                } else if let errorMessage {
                    errorState(errorMessage)
                } else {
                    emptyState
                }
            } else {
                TabView(selection: $currentPostId) {
                    ForEach($posts) { $post in
                        ReelPostView(
                            post: $post,
                            selfUsername: selfUsername,
                            onPlay: { trackId in
                                playingTrackId = trackId
                            },
                            onReact: {
                                if post.authorUsername == selfUsername {
                                    selectedPostForReactionList = post
                                } else {
                                    selectedPostForReaction = post
                                }
                            },
                            onComment: {
                                selectedPostForComments = post
                            },
                            onReport: {
                                reportTarget = ReportTarget(type: .post, targetId: Int64(post.id))
                            },
                            onBlock: {
                                blockTarget = BlockTarget(userId: Int64(post.authorId), username: post.authorUsername)
                            }
                        )
                        .padding(.bottom, 90)
                        .tag(post.id as Int?)
                        .onAppear { loadMoreIfNeeded(reaching: post.id) }
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea(.keyboard)
            }
        }
        .overlay(alignment: .top) {
            if isLoading && !posts.isEmpty {
                ProgressView().tint(.white).padding(.top, 8)
            }
        }
        .task {
            selfUsername = AuthService.shared.currentUsername
            if hasLoadedOnce {
                await refreshTop()
            } else {
                if loadTask == nil { startReload() }
                await loadTask?.value
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .postCreated)) { _ in
            startReload(showFirstPost: true)
        }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            startReload()
        }
        .onChange(of: currentPostId) { _, postId in
            if let postId { loadMoreIfNeeded(reaching: postId) }
        }
        .alert("Couldn't update reaction", isPresented: isShowingReactionError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(reactionError ?? "")
        }
        .blockConfirmation(target: $blockTarget) { blocked in
            // Hide their posts right away; the reload triggered by `.friendsChanged` confirms it.
            posts.removeAll { Int64($0.authorId) == blocked.userId }
            if !posts.contains(where: { $0.id == currentPostId }) {
                currentPostId = posts.first?.id
            }
        }
        // The feed is always dark, so everything that slides over it is dark as well.
        .sheet(item: $reportTarget) { target in
            ReportSheet(target: target)
                .preferredColorScheme(.dark)
        }

        .sheet(item: Binding(
            get: { playingTrackId.map { IdentifiableString(value: $0) } },
            set: { playingTrackId = $0?.value }
        )) { identifiableStr in
            SpotifyEmbedView(trackId: identifiableStr.value)
                .presentationDetents([.height(SpotifyEmbedView.sheetHeight)])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }

        .sheet(item: Binding(
            get: { selectedPostForReaction.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForReaction = $0?.value }
        )) { wrapper in
            EmojiPickerView { emoji in
                Task { await toggleReaction(postId: wrapper.value.id, emoji: emoji) }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .preferredColorScheme(.dark)
        }

        .sheet(item: Binding(
            get: { selectedPostForReactionList.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForReactionList = $0?.value }
        )) { wrapper in
            ReactionListView(postId: wrapper.value.id)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }

        .sheet(item: Binding(
            get: { selectedPostForComments.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForComments = $0?.value }
        )) { wrapper in
            let postId = wrapper.value.id
            CommentsScreen(postId: postId,
                           postAuthorId: wrapper.value.authorId,
                           onCommentAdded: { incrementCommentsCount(for: postId) })
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }
    }

    struct IdentifiableString: Identifiable {
        let value: String
        var id: String { value }
    }

    struct IdentifiablePost: Identifiable {
        let value: PostResponse
        var id: Int { value.id }
    }

    // MARK: - States

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "music.note.list")
                .font(.system(size: 44))
                .foregroundStyle(.white.opacity(0.8))
            Text("No posts from friends yet.")
                .font(.headline)
                .foregroundStyle(.white)
            Text("Add friends or share a track")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
            Button("Refresh") { startReload(showFirstPost: true) }
                .buttonStyle(.bordered)
                .tint(.white)
                .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
        .padding()
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundStyle(.white.opacity(0.8))
            Text("Couldn't load your feed")
                .font(.headline)
                .foregroundStyle(.white)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
            Button("Retry") { startReload(showFirstPost: true) }
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
        .padding()
    }

    private var isShowingReactionError: Binding<Bool> {
        Binding(get: { reactionError != nil }, set: { if !$0 { reactionError = nil } })
    }

    // MARK: - Loading

    /// Loads the first page again (new post, friends changed, retry), replacing any load in flight.
    private func startReload(showFirstPost: Bool = false) {
        loadTask?.cancel()
        loadGeneration += 1
        let generation = loadGeneration
        loadTask = Task { await fetchPage(0, generation: generation, showFirstPost: showFirstPost) }
    }

    /// Loads the next page once the last post comes into view.
    private func loadMoreIfNeeded(reaching postId: Int) {
        guard hasLoadedOnce, hasMore, loadTask == nil, postId == posts.last?.id else { return }
        let generation = loadGeneration
        let page = nextPage
        loadTask = Task { await fetchPage(page, generation: generation, showFirstPost: false) }
    }

    private func fetchPage(_ page: Int, generation: Int, showFirstPost: Bool) async {
        isLoading = true
        defer {
            if generation == loadGeneration {
                isLoading = false
                loadTask = nil
            }
        }
        do {
            let pagePosts = try await PostService.shared.getFeed(page: page, size: pageSize)
            guard generation == loadGeneration else { return }
            if page == 0 {
                posts = pagePosts.removingDuplicateIDs()
                if showFirstPost || !posts.contains(where: { $0.id == currentPostId }) {
                    currentPostId = posts.first?.id
                }
            } else {
                // New posts shift the server's pages, so a page may repeat posts we already have.
                let known = Set(posts.map(\.id))
                posts.append(contentsOf: pagePosts.filter { !known.contains($0.id) }.removingDuplicateIDs())
            }
            nextPage = page + 1
            hasMore = pagePosts.count == pageSize
            hasLoadedOnce = true
            errorMessage = nil
        } catch {
            guard generation == loadGeneration, !error.isCancellation else { return }
            // With posts on screen a failed reload or next page stays quiet; it is retried later.
            if posts.isEmpty { errorMessage = error.userMessage }
        }
    }

    /// Coming back to the tab: pick up new posts and fresh counts without losing the reader's place.
    private func refreshTop() async {
        guard loadTask == nil else { return }
        let generation = loadGeneration
        // A failed background refresh keeps what is on screen; the next visit tries again.
        guard let top = try? await PostService.shared.getFeed(page: 0, size: pageSize),
              generation == loadGeneration, loadTask == nil
        else { return }

        let fresh = Dictionary(top.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let known = Set(posts.map(\.id))
        let newPosts = top.filter { !known.contains($0.id) }.removingDuplicateIDs()
        posts = newPosts + posts.map { fresh[$0.id] ?? $0 }
        if known.isEmpty {
            nextPage = 1
            hasMore = top.count == pageSize
        }
        if currentPostId == nil || !posts.contains(where: { $0.id == currentPostId }) {
            currentPostId = posts.first?.id
        }
        errorMessage = nil
    }

    // MARK: - Actions

    private func incrementCommentsCount(for postId: Int) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        posts[index].commentsCount += 1
    }

    private func toggleReaction(postId: Int, emoji: String) async {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let snapshot = posts[index]

        var counts = snapshot.reactionsCount
        if let previous = snapshot.currentUserReaction {
            let remaining = (counts[previous] ?? 0) - 1
            counts[previous] = remaining > 0 ? remaining : nil
        }
        // Same emoji again removes the reaction, a different one replaces it (as on the server).
        let newReaction: String? = snapshot.currentUserReaction == emoji ? nil : emoji
        if let newReaction {
            counts[newReaction, default: 0] += 1
        }
        posts[index].currentUserReaction = newReaction
        posts[index].reactionsCount = counts

        do {
            try await PostService.shared.reactToPost(postId: postId, type: emoji)
        } catch {
            // Undo by id: the feed may have changed while the request was in flight.
            if let i = posts.firstIndex(where: { $0.id == postId }) {
                posts[i].currentUserReaction = snapshot.currentUserReaction
                posts[i].reactionsCount = snapshot.reactionsCount
            }
            if !error.isCancellation {
                reactionError = error.userMessage
            }
        }
    }
}

private extension Array where Element == PostResponse {
    func removingDuplicateIDs() -> [PostResponse] {
        var seen = Set<Int>()
        return filter { seen.insert($0.id).inserted }
    }
}
