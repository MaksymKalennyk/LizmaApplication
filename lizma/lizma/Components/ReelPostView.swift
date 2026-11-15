import SwiftUI

/// One full-page post in the feed. The feed is always dark, whatever the system appearance is.
struct ReelPostView: View {
    @Binding var post: PostResponse
    let selfUsername: String?
    let onPlay: (String) -> Void
    let onReact: () -> Void
    let onComment: () -> Void
    let onReport: () -> Void
    let onBlock: () -> Void

    @State private var captionExpanded = false

    /// Every action is this wide, so the column never shifts with the icons or the counts.
    private static let actionWidth: CGFloat = 52

    private var totalReactions: Int {
        post.reactionsCount.values.reduce(0, +)
    }

    private var isOwnPost: Bool {
        post.authorUsername == selfUsername
    }

    var body: some View {
        GeometryReader { proxy in
            // 75% of the page width, but never taller than half the page (landscape, small screens).
            let coverSide = min(proxy.size.width * 0.75, proxy.size.height * 0.5)

            VStack(spacing: 0) {
                Spacer(minLength: 0)

                cover(side: coverSide)

                Spacer(minLength: 0)

                HStack(alignment: .bottom, spacing: 12) {
                    details
                    actions
                }
                .shadow(color: .black.opacity(0.35), radius: 3, y: 1)
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(backdrop)
    }

    // MARK: - Author, caption, track

    private var details: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                AvatarView(username: post.authorUsername, size: 34)

                Text(post.authorUsername)
                    .font(.headline)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Text(RelativeTime.string(from: post.createdAt))
                    .font(.footnote)
                    .opacity(0.7)
                    .lineLimit(1)
                    .layoutPriority(1)
            }

            if let caption = post.caption, !caption.isEmpty {
                // Tap to read a long caption (still capped, so it can't push the cover off the page).
                Text(caption)
                    .font(.subheadline)
                    .lineLimit(captionExpanded ? 8 : 3)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.2)) { captionExpanded.toggle() }
                    }
            }

            Button(action: playTrack) {
                HStack(spacing: 10) {
                    Image(systemName: "music.note")
                        .font(.footnote.weight(.semibold))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(post.trackName)
                            .font(.footnote.weight(.semibold))
                            .lineLimit(1)
                        Text(post.artistName)
                            .font(.caption)
                            .opacity(0.75)
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Play \(post.trackName) by \(post.artistName)")
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Actions

    private var actions: some View {
        VStack(spacing: 18) {
            action("\(totalReactions)", perform: onReact) {
                if let myReaction = post.currentUserReaction {
                    Text(myReaction)
                        .font(.system(size: 28))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                } else {
                    Image(systemName: "heart")
                        .font(.system(size: 27))
                }
            }
            .accessibilityLabel(reactionAccessibilityLabel)
            .accessibilityValue("\(totalReactions) reactions")

            action("\(post.commentsCount)", perform: onComment) {
                Image(systemName: "bubble.right.fill")
                    .font(.system(size: 26))
            }
            .accessibilityLabel("Comments")
            .accessibilityValue("\(post.commentsCount)")

            action("Play", perform: playTrack) {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 34))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.black, .green)
            }
            .accessibilityLabel("Play track")

            if !isOwnPost {
                Menu {
                    Button {
                        onReport()
                    } label: {
                        Label("Report post", systemImage: "exclamationmark.bubble")
                    }
                    Button(role: .destructive) {
                        onBlock()
                    } label: {
                        Label("Block \(post.authorUsername)", systemImage: "hand.raised")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: Self.actionWidth, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("More options")
            }
        }
    }

    private func action<Icon: View>(_ caption: String,
                                    perform: @escaping () -> Void,
                                    @ViewBuilder icon: () -> Icon) -> some View {
        Button(action: perform) {
            VStack(spacing: 4) {
                icon()
                    .frame(width: Self.actionWidth, height: 36)
                Text(caption)
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
                    .lineLimit(1)
            }
            .frame(width: Self.actionWidth)
            .foregroundStyle(.white)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Cover

    private func cover(side: CGFloat) -> some View {
        Group {
            if let urlString = post.albumCoverUrl, let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    case .failure:
                        coverPlaceholder
                    default:
                        Rectangle().fill(.white.opacity(0.08))
                    }
                }
            } else {
                coverPlaceholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.45), radius: 20, y: 8)
        .contentShape(Rectangle())
        .onTapGesture(perform: playTrack)
        .accessibilityHidden(true)
    }

    /// For posts without artwork (or when it fails to load): looks intended rather than broken.
    private var coverPlaceholder: some View {
        ZStack {
            Rectangle().fill(.white.opacity(0.1))
            Image(systemName: "music.note")
                .font(.system(size: 64, weight: .light))
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private var backdrop: some View {
        Group {
            if let urlString = post.albumCoverUrl, let url = URL(string: urlString) {
                AsyncImage(url: url) { image in
                    image.resizable()
                        .aspectRatio(contentMode: .fill)
                        .blur(radius: 40)
                        .opacity(0.4)
                } placeholder: {
                    Color(UIColor.darkGray)
                }
            } else {
                LinearGradient(colors: [AvatarView.color(for: post.authorUsername).opacity(0.45), .black],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }

    private var reactionAccessibilityLabel: String {
        if isOwnPost { return "See who reacted" }
        if let myReaction = post.currentUserReaction { return "Change your reaction \(myReaction)" }
        return "React"
    }

    private func playTrack() {
        if post.musicProvider == "SPOTIFY" {
            let pureId = post.musicId.replacingOccurrences(of: "spotify:track:", with: "")
            onPlay(pureId)
        } else {
            if let url = URL(string: post.musicId) {
                UIApplication.shared.open(url)
            }
        }
    }
}
