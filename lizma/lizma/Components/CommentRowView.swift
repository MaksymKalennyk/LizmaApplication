import SwiftUI

struct CommentRowView: View {
    @Binding var comment: CommentResponse
    let isReply: Bool
    /// Report/block are only offered on other people's comments.
    let selfUsername: String?
    /// The user wrote the post: they may delete any comment under it, not only their own.
    let canDeleteAnyComment: Bool
    let onReply: (CommentResponse) -> Void
    let onLike: (Int) -> Void
    let onReport: (CommentResponse) -> Void
    let onBlock: (CommentResponse) -> Void
    /// Called once the user confirmed the deletion.
    let onDelete: (CommentResponse) -> Void
    /// Tapping the author's photo or name.
    var onOpenProfile: (String) -> Void = { _ in }

    @State private var isConfirmingDelete = false

    static let avatarSize: CGFloat = 36
    static let replyAvatarSize: CGFloat = 28
    static let avatarSpacing: CGFloat = 12

    private var isOwnComment: Bool {
        comment.authorUsername == selfUsername
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Only on this comment's own row, so a reply's menu targets the reply.
            commentRow
                .contentShape(Rectangle())
                .contextMenu {
                    if !isOwnComment {
                        Button {
                            onReport(comment)
                        } label: {
                            Label("Report comment", systemImage: "exclamationmark.bubble")
                        }
                        Button(role: .destructive) {
                            onBlock(comment)
                        } label: {
                            Label("Block \(comment.authorUsername)", systemImage: "hand.raised")
                        }
                    }
                    if isOwnComment || canDeleteAnyComment {
                        if !isOwnComment { Divider() }
                        Button(role: .destructive) {
                            isConfirmingDelete = true
                        } label: {
                            Label("Delete comment", systemImage: "trash")
                        }
                    }
                }
                // Attached to the row, so the question comes out of the comment it is about.
                .confirmationDialog("Delete this comment?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                    Button("Delete comment", role: .destructive) { onDelete(comment) }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    if comment.replies?.isEmpty == false {
                        Text("Its replies are deleted too. This can't be undone.")
                    } else {
                        Text("This can't be undone.")
                    }
                }

            if let replies = comment.replies, !replies.isEmpty {
                ForEach(Binding(get: { replies }, set: { comment.replies = $0 })) { $reply in
                    CommentRowView(
                        comment: $reply,
                        isReply: true,
                        selfUsername: selfUsername,
                        canDeleteAnyComment: canDeleteAnyComment,
                        onReply: onReply,
                        onLike: onLike,
                        onReport: onReport,
                        onBlock: onBlock,
                        onDelete: onDelete,
                        onOpenProfile: onOpenProfile
                    )
                    // Replies start where the parent's text starts.
                    .padding(.leading, Self.avatarSize + Self.avatarSpacing)
                }
            }
        }
    }

    private var commentRow: some View {
        HStack(alignment: .top, spacing: Self.avatarSpacing) {
            Button {
                onOpenProfile(comment.authorUsername)
            } label: {
                AvatarView(username: comment.authorUsername, imageURL: comment.authorAvatarUrl,
                           size: isReply ? Self.replyAvatarSize : Self.avatarSize)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(comment.authorName)'s profile")

            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Button {
                        onOpenProfile(comment.authorUsername)
                    } label: {
                        Text(comment.authorName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.primary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                    .buttonStyle(.plain)
                    Text(RelativeTime.string(from: comment.createdAt))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .layoutPriority(1)
                }

                Text(comment.content)
                    .font(.body)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    onReply(comment)
                } label: {
                    Text("Reply")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                onLike(comment.id)
            } label: {
                VStack(spacing: 2) {
                    Image(systemName: comment.isLikedByCurrentUser ? "heart.fill" : "heart")
                        .font(.system(size: 15))
                        .foregroundStyle(comment.isLikedByCurrentUser ? Color.red : Color.secondary)
                        .symbolEffect(.bounce, value: comment.isLikedByCurrentUser)
                    // Always there, so hearts line up whether or not a comment has likes.
                    Text(comment.likesCount > 0 ? "\(comment.likesCount)" : " ")
                        .font(.caption2)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }
                .frame(width: 36)
                .padding(.top, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(comment.isLikedByCurrentUser ? "Unlike" : "Like")
            .accessibilityValue("\(comment.likesCount) likes")
        }
        .id(comment.id)
    }
}
