import SwiftUI

/// One full-page post in the feed. The feed is always dark, whatever the system appearance is.
struct ReelPostView: View {
    @Binding var post: PostResponse
    /// The page runs up under the status bar; this keeps its content clear of it.
    var contentInsets = EdgeInsets()
    let selfUsername: String?
    /// Opens the track: preview here, the whole song in Apple Music or Spotify.
    let onPlay: () -> Void
    let onReact: () -> Void
    let onComment: () -> Void
    let onReport: () -> Void
    let onBlock: () -> Void
    /// Offered on the user's own posts instead of report and block; called once the user confirmed.
    var onDelete: () -> Void = {}
    /// Tapping the author's photo or name; nil where the profile is already open behind.
    var onOpenProfile: (() -> Void)? = nil

    @State private var captionExpanded = false
    @State private var isConfirmingDelete = false
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    /// Every action is this wide, so the column never shifts with the icons or the counts.
    private static let actionWidth: CGFloat = 52

    private var totalReactions: Int {
        post.reactionsCount.values.reduce(0, +)
    }

    private var isOwnPost: Bool {
        post.authorUsername == selfUsername
    }

    private var artworkURL: URL? {
        post.albumCoverUrl.flatMap { URL(string: $0) }
    }

    var body: some View {
        GeometryReader { proxy in
            let usable = CGSize(width: max(0, proxy.size.width - contentInsets.leading - contentInsets.trailing),
                                height: max(0, proxy.size.height - contentInsets.top - contentInsets.bottom))
            Group {
                if verticalSizeClass == .compact {
                    sideBySide(usable: usable)
                } else {
                    stacked(usable: usable)
                }
            }
            .padding(contentInsets)
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(backdrop)
        // Whatever doesn't fit must not spill into the posts above and below.
        .clipped()
    }

    /// Portrait (and iPad): the cover on top, the author, caption and actions under it.
    private func stacked(usable: CGSize) -> some View {
        // 75% of the page width, but never taller than half the usable height (small screens).
        let coverSide = max(0, min(usable.width * 0.75, usable.height * 0.5))
        return VStack(spacing: 0) {
            Spacer(minLength: 0)

            cover(side: coverSide)

            Spacer(minLength: 0)

            HStack(alignment: .bottom, spacing: 12) {
                details(captionLines: 3, expandedCaptionLines: 8)
                actions(spacing: 18)
            }
            .shadow(color: .black.opacity(0.35), radius: 3, y: 1)
            .padding(.horizontal)
            .padding(.bottom, 16)
        }
    }

    /// A phone in landscape is too low for that: the cover goes to the left, the rest beside it.
    private func sideBySide(usable: CGSize) -> some View {
        let coverSide = max(0, min(usable.height - 40, usable.width * 0.36))
        return HStack(alignment: .center, spacing: 24) {
            cover(side: coverSide)

            Group {
                details(captionLines: 2, expandedCaptionLines: 5)
                actions(spacing: 8)
            }
            .shadow(color: .black.opacity(0.35), radius: 3, y: 1)
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Author, caption, track

    private var author: some View {
        HStack(spacing: 8) {
            AvatarView(username: post.authorUsername, imageURL: post.authorAvatarUrl, size: 34)
            Text(post.authorName)
                .font(.headline)
                .lineLimit(1)
                .truncationMode(.middle)
        }
        .contentShape(Rectangle())
    }

    private func details(captionLines: Int, expandedCaptionLines: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                if let onOpenProfile {
                    Button(action: onOpenProfile) {
                        author
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens the profile")
                } else {
                    author
                }

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
                    .lineLimit(captionExpanded ? expandedCaptionLines : captionLines)
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

    private func actions(spacing: CGFloat) -> some View {
        VStack(spacing: spacing) {
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

            action(String(localized: "Play", bundle: .app), perform: playTrack) {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 34))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.black, .green)
            }
            .accessibilityLabel("Play track")

            Menu {
                if isOwnPost {
                    Button(role: .destructive) {
                        isConfirmingDelete = true
                    } label: {
                        Label("Delete post", systemImage: "trash")
                    }
                } else {
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
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: Self.actionWidth, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("More options")
            // Attached to the button, so the question comes out of the "⋯" that was tapped.
            .confirmationDialog("Delete this post?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("Delete post", role: .destructive, action: onDelete)
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Its comments and reactions are deleted too. This can't be undone.")
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
            if let url = artworkURL {
                RemoteImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: { failed in
                    if failed {
                        coverPlaceholder
                    } else {
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

    /// The artwork, blurred and dimmed, fills the page behind the post.
    private var backdrop: some View {
        Color.black
            .overlay {
                if let url = artworkURL {
                    RemoteImage(url: url) { image in
                        image.resizable()
                            .aspectRatio(contentMode: .fill)
                            .blur(radius: 40)
                            .opacity(0.4)
                    } placeholder: { _ in
                        Color(white: 0.12)
                    }
                } else {
                    LinearGradient(colors: [AvatarView.color(for: post.authorUsername).opacity(0.45), .black],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                }
            }
            .clipped()
            // Light artwork would wash out the status bar and the white text: darken the top edge and
            // the lower part of the page, whatever the picture is. It ends in black, where the page
            // meets the black strip the tab bar sits on.
            .overlay {
                LinearGradient(stops: [
                    .init(color: .black.opacity(0.3), location: 0),
                    .init(color: .clear, location: 0.16),
                    .init(color: .clear, location: 0.5),
                    .init(color: .black.opacity(0.6), location: 0.82),
                    .init(color: .black, location: 1)
                ], startPoint: .top, endPoint: .bottom)
            }
            .allowsHitTesting(false)
    }

    private var reactionAccessibilityLabel: String {
        if isOwnPost { return String(localized: "See who reacted", bundle: .app) }
        if let myReaction = post.currentUserReaction { return String(localized: "Change your reaction \(myReaction)", bundle: .app) }
        return String(localized: "React", bundle: .app)
    }

    private func playTrack() {
        onPlay()
    }
}
