import SwiftUI

struct CommentsScreen: View {
    let postId: Int
    @State private var comments: [CommentResponse] = []
    @State private var newCommentText = ""
    @State private var isLoading = false
    @State private var isPosting = false
    @State private var errorMessage: String? = nil
    
    @State private var replyTarget: CommentResponse? = nil
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if isLoading && comments.isEmpty {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else if let errorMessage = errorMessage {
                    Spacer()
                    Text(errorMessage).foregroundColor(.red)
                    Button("Retry") { Task { await loadComments() } }
                    Spacer()
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
                        }
                        .padding(.horizontal)
                    }
                    
                    HStack {
                        TextField(replyTarget == nil ? "Add a comment..." : "Write a reply...", text: $newCommentText)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .disabled(isPosting)
                        
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
        do {
            comments = try await PostService.shared.getComments(postId: postId)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func toggleLike(commentId: Int) async {
        
        var found = false
        for i in 0..<comments.count {
            if comments[i].id == commentId {
                comments[i].isLikedByCurrentUser.toggle()
                comments[i].likesCount += comments[i].isLikedByCurrentUser ? 1 : -1
                found = true
                break
            } else if var replies = comments[i].replies {
                for j in 0..<replies.count {
                    if replies[j].id == commentId {
                        replies[j].isLikedByCurrentUser.toggle()
                        replies[j].likesCount += replies[j].isLikedByCurrentUser ? 1 : -1
                        comments[i].replies = replies
                        found = true
                        break
                    }
                }
            }
            if found { break }
        }
        
        do {
            try await PostService.shared.toggleLikeComment(postId: postId, commentId: commentId)
        } catch {
            
            print("Failed to like comment: \(error)")
        }
    }
    
    private func postComment() async {
        let content = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { return }
        
        isPosting = true
        do {
            let req = CommentRequest(content: content, parentId: replyTarget?.id)
            let newComment = try await PostService.shared.addComment(postId: postId, req: req)
            
            if let target = replyTarget {
                
                for i in 0..<comments.count {
                    if comments[i].id == target.id || comments[i].id == target.parentId {
                        var nested = comments[i].replies ?? []
                        nested.append(newComment)
                        comments[i].replies = nested
                        break
                    }
                }
            } else {
                comments.append(newComment)
            }
            
            newCommentText = ""
            replyTarget = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        isPosting = false
    }
}

