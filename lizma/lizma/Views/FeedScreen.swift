import SwiftUI

struct FeedScreen: View {
    private let pageSize = 20

    @State private var posts: [PostResponse] = []
    /// The post on screen; keeps the reader's place when posts are added or reloaded.
    @State private var currentPostId: Int? = nil
    /// The post the reader last stopped on (or was sent to). Unlike `currentPostId` it doesn't move while
    /// the layout changes, so after a rotation the feed can be put back exactly on it.
    @State private var settledPostId: Int? = nil
    /// Height of one page, as laid out.
    @State private var pageHeight: CGFloat = 0
    @State private var nextPage = 0
    @State private var hasMore = true
    @State private var hasLoadedOnce = false
    @State private var isLoading = false
    /// The load in flight is a next page, not a reload of the first one.
    @State private var isLoadingMore = false
    /// The next page is requested this many posts before the end, so it is there when the reader arrives.
    private static let prefetchDistance = 3
    /// The single page load in flight (first page or next page).
    @State private var loadTask: Task<Void, Never>? = nil
    /// Bumped by every reload so results of superseded loads are dropped.
    @State private var loadGeneration = 0
    @State private var errorMessage: String? = nil
    @State private var reactionError: String? = nil

    /// How far the first post is pulled down, and whether that is far enough to reload on release.
    @State private var pullDistance: CGFloat = 0
    @State private var isPullArmed = false
    private static let refreshPullDistance: CGFloat = 90

    @State private var playingPost: PostResponse? = nil

    @State private var selectedPostForReaction: PostResponse? = nil
    @State private var selectedPostForReactionList: PostResponse? = nil
    @State private var selectedPostForComments: PostResponse? = nil
    @State private var reportTarget: ReportTarget? = nil
    @State private var blockTarget: BlockTarget? = nil
    @State private var deleteError: String? = nil
    /// The author whose profile is open over the feed.
    @State private var profileUsername: String? = nil
    @State private var selfUsername: String? = nil

    var body: some View {
        // Read here, outside the scroll view: the pages run up under the status bar and inset their own content.
        GeometryReader { screen in
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
                    // No bottom inset: the pages end where the tab bar begins.
                    pages(contentInsets: EdgeInsets(top: screen.safeAreaInsets.top,
                                                    leading: screen.safeAreaInsets.leading,
                                                    bottom: 0,
                                                    trailing: screen.safeAreaInsets.trailing))

                    // The black strip the tab bar sits on. A scroll view keeps drawing under the bars, and
                    // the bar's glass takes on whatever is beneath it: with artwork sliding by on every
                    // swipe (and the top of the next post showing through at rest) the bar would flash
                    // light and dark.
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        Color.black.frame(height: screen.safeAreaInsets.bottom)
                    }
                    .ignoresSafeArea(edges: [.bottom, .horizontal])
                    .allowsHitTesting(false)
                }
            }
        }
        // The keyboard of a sheet (comments) must not resize the pages underneath.
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .overlay(alignment: .top) {
            loadingIndicator
        }
        .sensoryFeedback(.impact(weight: .light), trigger: isPullArmed) { _, armed in armed }
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
        // Your name or photo changed (Edit profile): your posts here show the new ones right away.
        .onReceive(CurrentUserStore.shared.$me.compactMap { $0 }) { me in
            for index in posts.indices where posts[index].authorUsername == me.username {
                posts[index].authorDisplayName = me.displayName
                posts[index].authorAvatarUrl = me.avatarUrl
            }
        }
        .onChange(of: currentPostId) { _, postId in
            if let postId { loadMoreIfNeeded(reaching: postId) }
        }
        .onReceive(NotificationCenter.default.publisher(for: .feedTabReselected)) { _ in
            returnToTop()
        }
        .alert("Couldn't update reaction", isPresented: isShowingReactionError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(reactionError ?? "")
        }
        .blockConfirmation(target: $blockTarget) { blocked in
            // Hide their posts right away; the reload triggered by `.friendsChanged` confirms it.
            removePosts(byAuthorId: blocked.userId)
        }
        .sheet(item: Binding(get: { profileUsername.map { IdentifiableString(value: $0) } },
                             set: { profileUsername = $0?.value })) { wrapper in
            ProfileSheet(username: wrapper.value)
                .preferredColorScheme(.dark)
        }
        .alert("Couldn't delete post", isPresented: Binding(get: { deleteError != nil },
                                                            set: { if !$0 { deleteError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(deleteError ?? "")
        }
        // The feed is always dark, so everything that slides over it is dark as well.
        .sheet(item: $reportTarget) { target in
            ReportSheet(target: target)
                .preferredColorScheme(.dark)
        }

        .sheet(item: $playingPost) { post in
            TrackPlayerSheet(post: post)
                .presentationDetents([.height(post.previewUrl == nil && post.musicProvider != "SPOTIFY" ? 260 : 360)])
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
                           isOwnPost: wrapper.value.authorUsername == selfUsername,
                           onCommentAdded: { incrementCommentsCount(for: postId) },
                           onCommentsDeleted: { count in decrementCommentsCount(for: postId, by: count) })
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }
    }

    // MARK: - Pages

    /// One post per screen, swiped vertically. Each page is exactly as tall as the scroll view, so the
    /// paging lands on whole posts.
    private func pages(contentInsets: EdgeInsets) -> some View {
        ScrollViewReader { proxy in
            pager(contentInsets: contentInsets)
                // A new page height (rotation, split view) leaves the scroll view at its old offset,
                // between two posts: put the post the reader was on back at the top. On the next turn of
                // the run loop, once every page has its new size.
                .onChange(of: pageHeight) { oldHeight, newHeight in
                    guard oldHeight > 0, abs(newHeight - oldHeight) > 0.5, let postId = settledPostId else { return }
                    Task { @MainActor in
                        proxy.scrollTo(postId, anchor: .top)
                    }
                }
        }
    }

    private func pager(contentInsets: EdgeInsets) -> some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                ForEach($posts) { $post in
                    ReelPostView(
                        post: $post,
                        contentInsets: contentInsets,
                        selfUsername: selfUsername,
                        onPlay: {
                            playingPost = post
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
                        },
                        onDelete: {
                            Task { await deletePost(post) }
                        },
                        onOpenProfile: {
                            profileUsername = post.authorUsername
                        }
                    )
                    .containerRelativeFrame([.horizontal, .vertical])
                    // Measured on the pages themselves: during a rotation the height changes more than
                    // once (the bars settle after the screen has turned).
                    .onGeometryChange(for: CGFloat.self) { geometry in
                        geometry.size.height
                    } action: { height in
                        pageHeight = height
                    }
                    .onAppear { loadMoreIfNeeded(reaching: post.id) }
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        // With an anchor a programmatic scroll puts the post's top at the top; without one it only
        // moves "enough to make it visible", which can leave the feed between two posts.
        .scrollPosition(id: $currentPostId, anchor: .top)
        .scrollIndicators(.hidden)
        .modifier(WithoutScrollEdgeEffect())
        // Up under the status bar; the pages stop at the tab bar (see the black strip in `body`).
        .ignoresSafeArea(edges: [.top, .horizontal])
        // Pulling the first post down reloads the feed. Not `.refreshable`: its indicator would sit
        // under the status bar of this full-screen scroll view.
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            max(0, -(geometry.contentOffset.y + geometry.contentInsets.top))
        } action: { _, pulled in
            pullDistance = pulled
            let armed = pulled >= Self.refreshPullDistance
            if armed != isPullArmed { isPullArmed = armed }
        }
        .onScrollPhaseChange { oldPhase, newPhase in
            // The finger was lifted while the first post was pulled far enough.
            if oldPhase == .interacting, newPhase != .interacting, isPullArmed {
                startReload(showFirstPost: true)
            }
            // A swipe came to rest: this is where the reader is now. (Only the reader's own scrolling
            // counts, not what the system does to the offset during a rotation.)
            if newPhase == .idle, oldPhase == .interacting || oldPhase == .decelerating, let currentPostId {
                settledPostId = currentPostId
            }
        }
    }

    /// A spinner under the status bar: fades in while the feed is pulled down and stays while it reloads.
    /// A next page loads quietly ahead of the reader; it only shows if they reach the end before it arrives.
    @ViewBuilder
    private var loadingIndicator: some View {
        let isWaiting = isLoading && !posts.isEmpty && (!isLoadingMore || currentPostId == posts.last?.id)
        let progress = isWaiting ? 1 : min(1, pullDistance / Self.refreshPullDistance)
        if progress > 0.05 {
            ProgressView()
                .tint(.white)
                .padding(8)
                .background(.black.opacity(0.45), in: Circle())
                .opacity(progress)
                .scaleEffect(0.6 + 0.4 * progress)
                .padding(.top, 8)
                .accessibilityHidden(!isWaiting)
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

    /// Loads the next page when the reader gets close to the end of what is loaded.
    private func loadMoreIfNeeded(reaching postId: Int) {
        guard hasLoadedOnce, hasMore, loadTask == nil,
              let index = posts.firstIndex(where: { $0.id == postId }),
              index >= posts.count - Self.prefetchDistance
        else { return }
        let generation = loadGeneration
        let page = nextPage
        loadTask = Task { await fetchPage(page, generation: generation, showFirstPost: false) }
    }

    private func fetchPage(_ page: Int, generation: Int, showFirstPost: Bool) async {
        isLoading = true
        isLoadingMore = page > 0
        defer {
            if generation == loadGeneration {
                isLoading = false
                isLoadingMore = false
                loadTask = nil
            }
        }
        do {
            let pagePosts = try await PostService.shared.getFeed(page: page, size: pageSize)
            guard generation == loadGeneration else { return }
            if page == 0 {
                posts = pagePosts.removingDuplicateIDs()
                if showFirstPost || !posts.contains(where: { $0.id == currentPostId }) {
                    show(posts.first?.id)
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

    /// Coming back to the tab: pick up new posts and fresh counts. A reader at the top sees the newest
    /// post; further down they keep their place and the new posts wait above.
    private func refreshTop() async {
        guard loadTask == nil else { return }
        let generation = loadGeneration
        let wasAtTop = currentPostId == nil || currentPostId == posts.first?.id
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
        if wasAtTop || !posts.contains(where: { $0.id == currentPostId }) {
            show(posts.first?.id)
        }
        errorMessage = nil
    }

    /// Tapping the Feed tab again: back to the newest post, or a reload when already there.
    private func returnToTop() {
        guard let first = posts.first?.id else {
            startReload(showFirstPost: true)
            return
        }
        if currentPostId == first || currentPostId == nil {
            startReload(showFirstPost: true)
        } else {
            show(first, animated: true)
        }
    }

    /// Sends the reader to a post.
    private func show(_ postId: Int?, animated: Bool = false) {
        settledPostId = postId
        if animated {
            withAnimation { currentPostId = postId }
        } else {
            currentPostId = postId
        }
    }

    // MARK: - Actions

    /// Drops a blocked author's posts.
    private func removePosts(byAuthorId userId: Int64) {
        removePosts { Int64($0.authorId) == userId }
    }

    /// Drops posts from the feed. If the reader was on one of them, the next post takes its place.
    private func removePosts(where shouldRemove: (PostResponse) -> Bool) {
        let currentIndex = posts.firstIndex { $0.id == currentPostId } ?? 0
        let keptBefore = posts[..<currentIndex].filter { !shouldRemove($0) }.count
        posts.removeAll(where: shouldRemove)
        if !posts.contains(where: { $0.id == currentPostId }) {
            show(posts.isEmpty ? nil : posts[min(keptBefore, posts.count - 1)].id)
        }
    }

    private func deletePost(_ post: PostResponse) async {
        do {
            try await PostService.shared.deletePost(postId: post.id)
            removePosts { $0.id == post.id }
        } catch {
            guard !error.isCancellation else { return }
            deleteError = error.userMessage
        }
    }

    private func incrementCommentsCount(for postId: Int) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        posts[index].commentsCount += 1
    }

    private func decrementCommentsCount(for postId: Int, by count: Int) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        posts[index].commentsCount = max(0, posts[index].commentsCount - count)
    }

    private func toggleReaction(postId: Int, emoji: String) async {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let snapshot = posts[index]
        posts[index] = snapshot.togglingReaction(emoji)

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

/// The pages are full-screen pictures with their own dimming under the bars. The system's edge effect
/// (iOS 26) would add a second one, and it shows up as a light band under the tab bar while a sheet is open.
private struct WithoutScrollEdgeEffect: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.scrollEdgeEffectHidden(true, for: .all)
        } else {
            content
        }
    }
}

private extension Array where Element == PostResponse {
    func removingDuplicateIDs() -> [PostResponse] {
        var seen = Set<Int>()
        return filter { seen.insert($0.id).inserted }
    }
}
