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
    
    var summaryEmoji: String {
        post.reactionsCount.max(by: { $0.value < $1.value })?.key ?? "❤️"
    }
    
    var body: some View {
        VStack {
                Spacer()

                if let urlString = post.albumCoverUrl, let url = URL(string: urlString) {
                    AsyncImage(url: url) { image in
                        image.resizable()
                            .aspectRatio(contentMode: .fit)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.2))
                    }
                    .frame(width: UIScreen.main.bounds.width * 0.75, height: UIScreen.main.bounds.width * 0.75)
                    .cornerRadius(12)
                    .shadow(radius: 20)
                } else {
                    Rectangle().fill(Color.gray.opacity(0.3))
                        .frame(width: UIScreen.main.bounds.width * 0.75, height: UIScreen.main.bounds.width * 0.75)
                        .cornerRadius(12)
                }
                
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
                        .onTapGesture {
                            playTrack()
                        }
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
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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
