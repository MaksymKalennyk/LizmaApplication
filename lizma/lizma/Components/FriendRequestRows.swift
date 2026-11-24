import SwiftUI

/// A friend request someone sent to the user: accept or decline.
struct IncomingRequestRow: View {
    let request: FriendRequestDto
    let isBusy: Bool
    let accept: () -> Void
    let decline: () -> Void
    var openProfile: () -> Void = {}

    var body: some View {
        HStack(spacing: 12) {
            Button(action: openProfile) {
                UserLabel(username: request.requesterUsername, displayName: request.requesterDisplayName,
                          avatarUrl: request.requesterAvatarUrl)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

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

/// A friend request the user sent that hasn't been answered yet; it can be taken back.
struct SentRequestRow: View {
    let request: FriendRequestDto
    let isBusy: Bool
    let cancel: () -> Void
    var openProfile: () -> Void = {}

    var body: some View {
        HStack(spacing: 12) {
            Button(action: openProfile) {
                HStack(spacing: 12) {
                    AvatarView(username: request.recipientUsername, imageURL: request.recipientAvatarUrl)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(request.recipientDisplayName ?? request.recipientUsername)
                            .font(.body.weight(.medium))
                            .foregroundStyle(Color.primary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        Text("Pending")
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Spacer(minLength: 8)

            if isBusy {
                ProgressView()
            } else {
                Button("Cancel request", action: cancel)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .lineLimit(1)
                    .fixedSize()
                    .accessibilityLabel("Cancel the friend request to \(request.recipientUsername)")
            }
        }
    }
}
