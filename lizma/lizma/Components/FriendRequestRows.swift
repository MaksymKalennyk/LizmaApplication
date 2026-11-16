import SwiftUI

/// A friend request someone sent to the user: accept or decline.
struct IncomingRequestRow: View {
    let request: FriendRequestDto
    let isBusy: Bool
    let accept: () -> Void
    let decline: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(username: request.requesterUsername)

            Text(request.requesterUsername)
                .font(.body.weight(.medium))
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: 8)

            if isBusy {
                ProgressView()
            } else {
                HStack(spacing: 8) {
                    Button("Accept", action: accept)
                        .buttonStyle(.borderedProminent)
                        .accessibilityLabel("Accept \(request.requesterUsername)'s friend request")
                    Button("Decline", action: decline)
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Decline \(request.requesterUsername)'s friend request")
                }
                .controlSize(.small)
                .lineLimit(1)
                .fixedSize()
            }
        }
    }
}

/// A friend request the user sent that hasn't been answered yet.
struct SentRequestRow: View {
    let request: FriendRequestDto

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(username: request.recipientUsername)

            Text(request.recipientUsername)
                .font(.body.weight(.medium))
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: 8)

            Text("Pending")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}
