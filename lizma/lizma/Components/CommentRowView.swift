import SwiftUI

struct CommentRowView: View {
    @Binding var comment: CommentResponse
    let isReply: Bool
    let onReply: (CommentResponse) -> Void
    let onLike: (Int) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: isReply ? 28 : 36, height: isReply ? 28 : 36)
                    .overlay(Text(String(comment.authorUsername.prefix(1).uppercased())).font(isReply ? .caption : .subheadline).foregroundColor(.primary))
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(comment.authorUsername).font(.subheadline).bold()
                        Text(formatDate(comment.createdAt)).font(.caption2).foregroundColor(.secondary)
                    }
                    Text(comment.content).font(.body)
                    
                    HStack(spacing: 16) {
                        Button {
                            onReply(comment)
                        } label: {
                            Text("Reply").font(.caption).foregroundColor(.secondary).bold()
                        }
                    }
                    .padding(.top, 2)
                }
                
                Spacer()

                Button {
                    onLike(comment.id)
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: comment.isLikedByCurrentUser ? "heart.fill" : "heart")
                            .foregroundColor(comment.isLikedByCurrentUser ? .red : .gray)
                            .font(.system(size: 14))
                        if comment.likesCount > 0 {
                            Text("\(comment.likesCount)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }

            if let replies = comment.replies, !replies.isEmpty {
                ForEach(Binding(get: { replies }, set: { comment.replies = $0 })) { $reply in
                    CommentRowView(
                        comment: $reply,
                        isReply: true,
                        onReply: onReply, 
                        onLike: onLike
                    )
                    .padding(.leading, 40)
                    .padding(.top, 4)
                }
            }
        }
    }
    
    private func formatDate(_ dateString: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: dateString) ?? ISO8601DateFormatter().date(from: dateString) else {
            return dateString
        }
        let displayFormatter = RelativeDateTimeFormatter()
        displayFormatter.unitsStyle = .abbreviated
        return displayFormatter.localizedString(for: date, relativeTo: Date())
    }
}
