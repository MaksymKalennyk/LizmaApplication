import SwiftUI

/// Posts opened from a profile: full-screen pages like the feed, starting at the tapped post, with the
/// same actions (play, react, comment, report, block, delete your own).
struct PostPagerView: View {
    @Binding var posts: [PostResponse]
    let startPostId: Int
    /// Called when the last post comes into view, to load older ones.
    var onReachEnd: () -> Void = {}
    /// Called after the user blocked the author: the profile behind is gone too.
    var onAuthorBlocked: () -> Void = {}
    /// Opens the first post's comments right away (a notification about a comment).
    var opensComments = false

    @Environment(\.dismiss) private var dismiss
    @State private var currentPostId: Int?
    @State private var selfUsername: String?

    @State private var playingPost: PostResponse?
    @State private var reactingTo: PostResponse?
    @State private var reactionsOf: PostResponse?
    @State private var commentsOf: PostResponse?
    @State private var reportTarget: ReportTarget?
    @State private var blockTarget: BlockTarget?
    @State private var errorMessage: String?

    init(posts: Binding<[PostResponse]>, startPostId: Int, opensComments: Bool = false,
         onReachEnd: @escaping () -> Void = {}, onAuthorBlocked: @escaping () -> Void = {}) {
        _posts = posts
        self.startPostId = startPostId
        self.opensComments = opensComments
        self.onReachEnd = onReachEnd
        self.onAuthorBlocked = onAuthorBlocked
        _currentPostId = State(initialValue: startPostId)
    }

    var body: some View {
        GeometryReader { screen in
            ZStack(alignment: .topLeading) {
                Color.black.ignoresSafeArea()

                ScrollViewReader { proxy in
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0) {
                            ForEach($posts) { $post in
                                page(for: $post, insets: EdgeInsets(top: screen.safeAreaInsets.top,
                                                                     leading: screen.safeAreaInsets.leading,
                                                                     bottom: screen.safeAreaInsets.bottom,
                                                                     trailing: screen.safeAreaInsets.trailing))
                                    .containerRelativeFrame([.horizontal, .vertical])
                                    .id(post.id)
                                    .onAppear {
                                        if post.id == posts.last?.id { onReachEnd() }
                                    }
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollTargetBehavior(.paging)
                    .scrollPosition(id: $currentPostId, anchor: .top)
                    .scrollIndicators(.hidden)
                    .ignoresSafeArea()
                    .onAppear {
                        // On the next turn, once the pages are laid out.
                        DispatchQueue.main.async { proxy.scrollTo(startPostId, anchor: .top) }
                    }
                }

                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.black.opacity(0.35), in: Circle())
                }
                .padding(.leading, 12 + screen.safeAreaInsets.leading)
                .padding(.top, 4 + screen.safeAreaInsets.top)
                .accessibilityLabel("Close")
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .task {
            selfUsername = AuthService.shared.currentUsername
            if opensComments, commentsOf == nil {
                // After the page has finished sliding in: a sheet can't open during that.
                try? await Task.sleep(for: .milliseconds(450))
                commentsOf = posts.first { $0.id == startPostId }
            }
        }
        .alert("Something went wrong", isPresented: Binding(get: { errorMessage != nil },
                                                            set: { if !$0 { errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
        .blockConfirmation(target: $blockTarget) { _ in
            dismiss()
            onAuthorBlocked()
        }
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
        .sheet(item: $reactingTo) { post in
            EmojiPickerView { emoji in
                Task { await react(to: post.id, with: emoji) }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .preferredColorScheme(.dark)
        }
        .sheet(item: $reactionsOf) { post in
            ReactionListView(postId: post.id)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }
        .sheet(item: $commentsOf) { post in
            CommentsScreen(postId: post.id,
                           postAuthorId: post.authorId,
                           isOwnPost: post.authorUsername == selfUsername,
                           onCommentAdded: { changeCommentsCount(of: post.id, by: 1) },
                           onCommentsDeleted: { count in changeCommentsCount(of: post.id, by: -count) })
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .preferredColorScheme(.dark)
        }
    }

    private func page(for post: Binding<PostResponse>, insets: EdgeInsets) -> some View {
        let value = post.wrappedValue
        return ReelPostView(
            post: post,
            contentInsets: insets,
            selfUsername: selfUsername,
            onPlay: { playingPost = value },
            onReact: {
                if value.authorUsername == selfUsername {
                    reactionsOf = value
                } else {
                    reactingTo = value
                }
            },
            onComment: { commentsOf = value },
            onReport: { reportTarget = ReportTarget(type: .post, targetId: Int64(value.id)) },
            onBlock: { blockTarget = BlockTarget(userId: Int64(value.authorId), username: value.authorUsername) },
            onDelete: { Task { await delete(value) } }
        )
    }

    private func react(to postId: Int, with emoji: String) async {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let snapshot = posts[index]
        posts[index] = snapshot.togglingReaction(emoji)
        do {
            try await PostService.shared.reactToPost(postId: postId, type: emoji)
        } catch {
            if let i = posts.firstIndex(where: { $0.id == postId }) { posts[i] = snapshot }
            if !error.isCancellation { errorMessage = error.userMessage }
        }
    }

    private func delete(_ post: PostResponse) async {
        do {
            try await PostService.shared.deletePost(postId: post.id)
            let index = posts.firstIndex { $0.id == post.id } ?? 0
            posts.removeAll { $0.id == post.id }
            if posts.isEmpty {
                dismiss()
            } else {
                currentPostId = posts[min(index, posts.count - 1)].id
            }
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }

    private func changeCommentsCount(of postId: Int, by delta: Int) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        posts[index].commentsCount = max(0, posts[index].commentsCount + delta)
    }
}
