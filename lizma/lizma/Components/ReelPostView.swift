import SwiftUI

struct ReelPostView: View {
    @Binding var post: PostResponse
    let selfUsername: String?
    let onPlay: (String) -> Void
    let onReact: () -> Void
    let onComment: () -> Void

    var totalReactions: Int {
        post.reactionsCount.values.reduce(0, +)
    }

    private var isOwnPost: Bool {
        post.authorUsername == selfUsername
    }

    var body: some View {
        GeometryReader { proxy in
            // 75% of the page width, but never taller than half the page (landscape, small screens).
            let coverSide = min(proxy.size.width * 0.75, proxy.size.height * 0.5)

            VStack {
                Spacer()

                cover(side: coverSide)

                Spacer()

                HStack(alignment: .bottom) {

                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(width: 32, height: 32)
                                .overlay(Text(String(post.authorUsername.prefix(1).uppercased())).foregroundColor(.white))

                            Text(post.authorUsername)
                                .font(.headline)
                                .foregroundColor(.white)
                                .bold()
                        }

                        if let caption = post.caption, !caption.isEmpty {
                            Text(caption)
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .lineLimit(3)
                        }

                        Button(action: playTrack) {
                            HStack {
                                Image(systemName: "music.note")
                                    .foregroundColor(.white)
                                Text("\(post.trackName) • \(post.artistName)")
                                    .font(.footnote)
                                    .foregroundColor(.white)
                                    .bold()
                            }
                            .padding(8)
                            .background(Color.black.opacity(0.4))
                            .cornerRadius(16)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Play \(post.trackName) by \(post.artistName)")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Spacer(minLength: 16)

                    VStack(spacing: 24) {

                        Button {
                            onReact()
                        } label: {
                            VStack(spacing: 4) {
                                if let myReaction = post.currentUserReaction {
                                    Text(myReaction).font(.system(size: 30))
                                } else {
                                    Image(systemName: "heart")
                                        .font(.system(size: 28))
                                        .foregroundColor(.white)
                                }
                                Text("\(totalReactions)")
                                    .font(.caption)
                                    .foregroundColor(.white)
                                    .bold()
                            }
                        }
                        .accessibilityLabel(reactionAccessibilityLabel)
                        .accessibilityValue("\(totalReactions) reactions")

                        Button {
                            onComment()
                        } label: {
                            VStack(spacing: 4) {
                                Image(systemName: "bubble.right.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white)
                                Text("\(post.commentsCount)")
                                    .font(.caption)
                                    .foregroundColor(.white)
                                    .bold()
                            }
                        }
                        .accessibilityLabel("Comments")
                        .accessibilityValue("\(post.commentsCount)")

                        Button {
                            playTrack()
                        } label: {
                            VStack(spacing: 4) {
                                Image(systemName: "play.circle.fill")
                                    .font(.system(size: 32))
                                    .foregroundColor(.green)
                                Text("Play")
                                    .font(.caption)
                                    .foregroundColor(.white)
                                    .bold()
                            }
                        }
                        .accessibilityLabel("Play track")
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(
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
                    LinearGradient(colors: [Color.blue.opacity(0.3), Color.black], startPoint: .topLeading, endPoint: .bottomTrailing)
                }
            }
            .allowsHitTesting(false)
            .edgesIgnoringSafeArea(.all)
        )
    }

    @ViewBuilder
    private func cover(side: CGFloat) -> some View {
        if let urlString = post.albumCoverUrl, let url = URL(string: urlString) {
            AsyncImage(url: url) { image in
                image.resizable()
                    .aspectRatio(contentMode: .fit)
            } placeholder: {
                Rectangle().fill(Color.gray.opacity(0.2))
            }
            .frame(width: side, height: side)
            .cornerRadius(12)
            .shadow(radius: 20)
            .accessibilityHidden(true)
        } else {
            Rectangle().fill(Color.gray.opacity(0.3))
                .frame(width: side, height: side)
                .cornerRadius(12)
                .accessibilityHidden(true)
        }
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
