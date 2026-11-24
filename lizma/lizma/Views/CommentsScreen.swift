import SwiftUI

struct CommentsScreen: View {
    let postId: Int
    /// Blocking the post's author hides the whole post, so the sheet closes.
    let postAuthorId: Int
    /// The user wrote the post: they may delete anyone's comment under it.
    var isOwnPost = false
    /// Called after a comment or reply was posted (the feed bumps its comment count).
    var onCommentAdded: () -> Void = {}
    /// Called after a deletion with how many comments went (a top-level one takes its replies).
    var onCommentsDeleted: (Int) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss

    @State private var comments: [CommentResponse] = []
    @State private var newCommentText = ""
    @State private var isLoading = false
    @State private var hasLoaded = false
    @State private var isPosting = false
    /// Loading the list failed: shown instead of the (empty) list, with Retry.
    @State private var loadError: String? = nil
    /// Posting or liking failed: shown above the input, the list stays visible.
    @State private var actionError: String? = nil
    @State private var likesInFlight: Set<Int> = []

    @State private var replyTarget: CommentResponse? = nil
    @FocusState private var isInputFocused: Bool
    /// The comment to bring into view (the one just posted).
    @State private var scrollTarget: Int? = nil

    @State private var selfUsername: String? = nil
    @State private var reportTarget: ReportTarget? = nil
    @State private var blockTarget: BlockTarget? = nil
    /// Whose profile is open on top of the comments.
    @State private var profileUsername: String? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                commentList
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider()

                composer
            }
            .navigationTitle("Comments")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $profileUsername) { username in
                UserProfileScreen(username: username)
            }
            .sheet(item: $reportTarget) { target in
                // Presented over the dark feed, like this sheet.
                ReportSheet(target: target)
                    .preferredColorScheme(.dark)
            }
            .blockConfirmation(target: $blockTarget) { blocked in
                if blocked.userId == Int64(postAuthorId) {
                    // Their post is gone from the feed as well: nothing left to show here.
                    dismiss()
                    return
                }
                // Hide their comments right away, then reload what the server now shows.
                removeComments(byAuthorId: blocked.userId)
                Task { await loadComments() }
            }
            .task {
                selfUsername = AuthService.shared.currentUsername
                await loadComments()
            }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var commentList: some View {
        if comments.isEmpty {
            if let loadError, !isLoading {
                ContentUnavailableView {
                    Label("Couldn't load comments", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(loadError)
                } actions: {
                    Button("Retry") { Task { await loadComments() } }
                        .buttonStyle(.bordered)
                }
            } else if isLoading || !hasLoaded {
                ProgressView()
            } else {
                ContentUnavailableView(
                    "No comments yet",
                    systemImage: "bubble.left.and.bubble.right",
                    description: Text("Be the first to comment.")
                )
            }
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        ForEach($comments) { $comment in
                            CommentRowView(
                                comment: $comment,
                                isReply: false,
                                selfUsername: selfUsername,
                                canDeleteAnyComment: isOwnPost,
                                onReply: { target in
                                    replyTarget = target
                                    isInputFocused = true
                                },
                                onLike: { targetId in Task { await toggleLike(commentId: targetId) } },
                                onReport: { target in
                                    reportTarget = ReportTarget(type: .comment, targetId: Int64(target.id))
                                },
                                onBlock: { target in
                                    blockTarget = BlockTarget(userId: Int64(target.authorId),
                                                              username: target.authorUsername)
                                },
                                onDelete: { target in
                                    Task { await deleteComment(target) }
                                },
                                onOpenProfile: { username in
                                    profileUsername = username
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: scrollTarget) { _, target in
                    guard let target else { return }
                    withAnimation { proxy.scrollTo(target, anchor: .bottom) }
                    scrollTarget = nil
                }
            }
        }
    }

    /// Reply banner, error line and the input field.
    private var composer: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let target = replyTarget {
                HStack(spacing: 6) {
                    Image(systemName: "arrowshape.turn.up.left.fill")
                        .font(.caption2)
                    Text("Replying to \(target.authorUsername)")
                        .font(.caption)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Spacer(minLength: 8)
                    Button {
                        replyTarget = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Cancel reply")
                }
                .foregroundStyle(.secondary)
                .padding(.horizontal, 6)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            if let actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 6)
            }

            CommentInputBar(
                text: $newCommentText,
                placeholder: replyTarget == nil ? "Add a comment…" : "Write a reply…",
                isSending: isPosting,
                isFocused: $isInputFocused,
                onSend: { Task { await postComment() } }
            )
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .animation(.easeInOut(duration: 0.2), value: replyTarget?.id)
    }

    // MARK: - Actions

    private func loadComments() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            comments = try await PostService.shared.getComments(postId: postId)
            hasLoaded = true
        } catch {
            guard !error.isCancellation else { return }
            loadError = error.userMessage
        }
    }

    private func toggleLike(commentId: Int) async {
        // One request per comment at a time keeps the optimistic state and the server in step.
        guard !likesInFlight.contains(commentId), let original = comment(withId: commentId) else { return }
        likesInFlight.insert(commentId)
        defer { likesInFlight.remove(commentId) }
        actionError = nil

        let liked = !original.isLikedByCurrentUser
        updateComment(id: commentId) {
            $0.isLikedByCurrentUser = liked
            $0.likesCount = max(0, original.likesCount + (liked ? 1 : -1))
        }

        do {
            try await PostService.shared.toggleLikeComment(postId: postId, commentId: commentId)
        } catch {
            updateComment(id: commentId) {
                $0.isLikedByCurrentUser = original.isLikedByCurrentUser
                $0.likesCount = original.likesCount
            }
            guard !error.isCancellation else { return }
            actionError = String(localized: "Couldn't update like: \(error.userMessage)", bundle: .app)
        }
    }

    private func postComment() async {
        let content = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty, !isPosting else { return }

        // The field stays enabled while posting, so the keyboard never drops.
        isPosting = true
        actionError = nil
        defer { isPosting = false }

        // Replies are one level deep: answering a reply goes to the same top-level comment.
        let parentId = replyTarget.map { $0.parentId ?? $0.id }
        do {
            let req = CommentRequest(content: content, parentId: parentId)
            let newComment = try await PostService.shared.addComment(postId: postId, req: req)

            if let parentId, let index = comments.firstIndex(where: { $0.id == parentId }) {
                comments[index].replies = (comments[index].replies ?? []) + [newComment]
            } else {
                comments.append(newComment)
            }

            // Keep anything typed while the request was in flight.
            if newCommentText.trimmingCharacters(in: .whitespacesAndNewlines) == content {
                newCommentText = ""
            }
            replyTarget = nil
            scrollTarget = newComment.id
            onCommentAdded()
        } catch {
            guard !error.isCancellation else { return }
            actionError = postFailureMessage(error)
        }
    }

    /// When the server explains what is wrong with the comment (e.g. the word filter), its message
    /// reads fine by itself; other failures (network, server errors) get some context.
    private func postFailureMessage(_ error: Error) -> String {
        if case APIError.server(let code, let message) = error, (400..<500).contains(code) {
            return message
        }
        return String(localized: "Couldn't post comment: \(error.userMessage)", bundle: .app)
    }

    private func deleteComment(_ target: CommentResponse) async {
        actionError = nil
        do {
            try await PostService.shared.deleteComment(postId: postId, commentId: target.id)
        } catch {
            guard !error.isCancellation else { return }
            actionError = String(localized: "Couldn't delete comment: \(error.userMessage)", bundle: .app)
            return
        }

        // A top-level comment goes with its replies; a reply goes alone.
        var removed = 0
        if let index = comments.firstIndex(where: { $0.id == target.id }) {
            removed = 1 + (comments[index].replies?.count ?? 0)
            comments.remove(at: index)
        } else {
            for i in comments.indices {
                if let j = comments[i].replies?.firstIndex(where: { $0.id == target.id }) {
                    comments[i].replies?.remove(at: j)
                    removed = 1
                    break
                }
            }
        }
        if let reply = replyTarget, reply.id == target.id || reply.parentId == target.id {
            replyTarget = nil
        }
        if removed > 0 {
            onCommentsDeleted(removed)
        }
    }

    private func removeComments(byAuthorId userId: Int64) {
        comments.removeAll { Int64($0.authorId) == userId }
        for i in comments.indices {
            comments[i].replies?.removeAll { Int64($0.authorId) == userId }
        }
        if let target = replyTarget, Int64(target.authorId) == userId {
            replyTarget = nil
        }
    }

    private func comment(withId id: Int) -> CommentResponse? {
        for comment in comments {
            if comment.id == id { return comment }
            if let reply = comment.replies?.first(where: { $0.id == id }) { return reply }
        }
        return nil
    }

    private func updateComment(id: Int, _ change: (inout CommentResponse) -> Void) {
        for i in comments.indices {
            if comments[i].id == id {
                change(&comments[i])
                return
            }
            if var replies = comments[i].replies, let j = replies.firstIndex(where: { $0.id == id }) {
                change(&replies[j])
                comments[i].replies = replies
                return
            }
        }
    }
}
