import SwiftUI

/// A user's round picture, used wherever a user is shown: their profile photo, or a badge with their
/// initial while there is none (or it is loading). The badge colour comes from the username, so the
/// same person looks the same on every screen.
struct AvatarView: View {
    let username: String
    /// The profile photo, as the server gives it ("/avatars/…"); nil shows the initial.
    var imageURL: String? = nil
    var size: CGFloat = 36

    var body: some View {
        Group {
            if let url = AppConfig.mediaURL(imageURL) {
                RemoteImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: { _ in
                    initialBadge
                }
            } else {
                initialBadge
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var initialBadge: some View {
        Circle()
            .fill(Self.color(for: username).gradient)
            .overlay(
                Text(Self.initial(of: username))
                    .font(.system(size: size * 0.44, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
            )
    }

    /// The first letter or digit, uppercased (usernames may start with "_", "." or "-").
    static func initial(of username: String) -> String {
        let first = username.first(where: { $0.isLetter || $0.isNumber }) ?? username.first
        return first.map { String($0).uppercased() } ?? "?"
    }

    /// Colours that keep white text readable in light and dark mode.
    private static let palette: [Color] = [.blue, .indigo, .purple, .pink, .red, .orange, .teal, .green]

    static func color(for username: String) -> Color {
        // Not `hashValue`: that one changes on every launch.
        var hash: UInt64 = 5381
        for byte in username.lowercased().utf8 {
            hash = (hash &* 33) ^ UInt64(byte)
        }
        return palette[Int(hash % UInt64(palette.count))]
    }
}

#Preview {
    HStack {
        AvatarView(username: "anna")
        AvatarView(username: "boris", size: 28)
        AvatarView(username: "_clara", size: 56)
    }
    .padding()
}
