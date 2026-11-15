import SwiftUI

struct CommentsScreen: View {
    let postId: Int
    /// Called after a comment or reply was posted (the feed bumps its comment count).
    var onCommentAdded: () -> Void = {}

    @State private var comments: [CommentResponse] = []
    @State private var newCommentText = ""
    @State private var isLoading = false
    @State private var hasLoaded = false
    @State private var isPosting = false
    /// Loading the list failed: shown instead of the (empty) list, with Retry.
    @State private var loadError: String? = nil
    /// Posting or liking failed: shown next to the input, the list stays visible.
    @State private var actionError: String? = nil
    @State private var likesInFlight: Set<Int> = []

    @State private var replyTarget: CommentResponse? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Group {
                    if comments.isEmpty {
                        if let loadError, !isLoading {
                            VStack(spacing: 12) {
                                Text(loadError)
                                    .foregroundColor(.red)
                                    .multilineTextAlignment(.center)
                                Button("Retry") { Task { await loadComments() } }
                            }
                            .padding()
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
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 16) {
                                ForEach($comments) { $comment in
                                    CommentRowView(
                                        comment: $comment,
                                        isReply: false,
                                        onReply: { target in replyTarget = target },
                                        onLike: { targetId in Task { await toggleLike(commentId: targetId) } }
                                    )
                                }
                            }
                            .padding()
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider()

                VStack(spacing: 8) {
                    if let target = replyTarget {
                        HStack {
                            Text("Replying to @\(target.authorUsername)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer()
                            Button {
                                replyTarget = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                            }
                            .accessibilityLabel("Cancel reply")
                        }
                        .padding(.horizontal)
                    }

                    if let actionError {
                        Text(actionError)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal)
                    }

                    HStack {
                        TextField(replyTarget == nil ? "Add a comment..." : "Write a reply...", text: $newCommentText)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .disabled(isPosting)
                            .submitLabel(.send)
                            .onSubmit { Task { await postComment() } }

                        Button("Post") {
                            Task { await postComment() }
                        }
                        .disabled(newCommentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isPosting)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                }
                .padding(.top, 8)
                .background(Color(UIColor.systemBackground))
            }
            .navigationTitle("Comments")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await loadComments()
            }
        }
    }

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
            actionError = "Couldn't update like: \(error.userMessage)"
        }
    }

    private func postComment() async {
        let content = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty, !isPosting else { return }

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

            newCommentText = ""
            replyTarget = nil
            onCommentAdded()
        } catch {
            guard !error.isCancellation else { return }
            actionError = "Couldn't post comment: \(error.userMessage)"
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
