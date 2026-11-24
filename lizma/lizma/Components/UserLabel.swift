import SwiftUI

/// A user in a list row: photo and name, with the @username under a display name.
struct UserLabel: View {
    let username: String
    let displayName: String?
    let avatarUrl: String?
    var size: CGFloat = 36

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(username: username, imageURL: avatarUrl, size: size)
            VStack(alignment: .leading, spacing: 1) {
                Text(displayName ?? username)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.primary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if displayName != nil {
                    Text(verbatim: "@\(username)")
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}
