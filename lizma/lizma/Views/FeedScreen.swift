import SwiftUI

let availableEmojis = ["❤️", "🔥", "🎵", "🙌", "😂", "😢"]

struct FeedScreen: View {
    @State private var posts: [PostResponse] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    @State private var playingTrackId: String? = nil

    @State private var selectedPostForReaction: PostResponse? = nil
    @State private var selectedPostForReactionList: PostResponse? = nil
    @State private var selectedPostForComments: PostResponse? = nil
    @State private var selfUsername: String? = nil
    
    var body: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)
            
            if isLoading && posts.isEmpty {
                ProgressView().tint(.white)
            } else if let errorMessage = errorMessage {
                VStack {
                    Text(errorMessage).foregroundColor(.red)
                    Button("Retry") {
                        Task { await loadFeed() }
                    }
                }
            } else if posts.isEmpty {
                Text("No posts from friends yet.")
                    .foregroundColor(.secondary)
            } else {
                TabView {
                    ForEach($posts) { $post in
                        ReelPostView(
                            post: $post,
                            selfUsername: selfUsername,
                            onPlay: { trackId in
                                playingTrackId = trackId
                            },
                            onReact: {
                                if post.authorUsername == selfUsername {
                                    selectedPostForReactionList = post
                                } else {
                                    selectedPostForReaction = post
                                }
                            },
                            onComment: {
                                selectedPostForComments = post
                            }
                        )
                        .padding(.bottom, 90)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea(.keyboard)
            }
        }
        .task {
            await loadFeed()
            selfUsername = currentUsernameFromTokenFeed()
        }
        
        .sheet(item: Binding(
            get: { playingTrackId.map { IdentifiableString(value: $0) } },
            set: { playingTrackId = $0?.value }
        )) { identifiableStr in
            SpotifyEmbedView(trackId: identifiableStr.value)
                .presentationDetents([.height(200)])
                .presentationDragIndicator(.visible)
        }
        
        .sheet(item: Binding(
            get: { selectedPostForReaction.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForReaction = $0?.value }
        )) { wrapper in
            EmojiPickerView { emoji in
                Task { await toggleReaction(for: wrapper.value, emoji: emoji) }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        
        .sheet(item: Binding(
            get: { selectedPostForReactionList.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForReactionList = $0?.value }
        )) { wrapper in
            ReactionListView(postId: wrapper.value.id)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        
        .sheet(item: Binding(
            get: { selectedPostForComments.map { IdentifiablePost(value: $0) } },
            set: { selectedPostForComments = $0?.value }
        )) { wrapper in
            CommentsScreen(postId: wrapper.value.id)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }
    
    struct IdentifiableString: Identifiable {
        let value: String
        var id: String { value }
    }
    
    struct IdentifiablePost: Identifiable {
        let value: PostResponse
        var id: Int { value.id }
    }
    
    private func currentUsernameFromTokenFeed() -> String? {
        guard let token = try? Keychain.get("jwtToken") else { return nil }
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }
        
        var base64 = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        
        let pad = 4 - (base64.count % 4)
        if pad < 4 { base64 += String(repeating: "=", count: pad) }
        
        guard let payloadData = Data(base64Encoded: base64),
              let obj = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any]
        else { return nil }
        
        return (obj["username"] as? String) ?? (obj["sub"] as? String)
    }

    private func loadFeed() async {
        isLoading = true
        errorMessage = nil
        do {
            posts = try await PostService.shared.getFeed()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func toggleReaction(for post: PostResponse, emoji: String) async {
        guard let index = posts.firstIndex(where: { $0.id == post.id }) else { return }

        let originalReaction = posts[index].currentUserReaction
        var counts = posts[index].reactionsCount

        if let orig = originalReaction {
            counts[orig] = max(0, (counts[orig] ?? 0) - 1)
        }
        
        if originalReaction == emoji {
            
            posts[index].currentUserReaction = nil
        } else {
            
            posts[index].currentUserReaction = emoji
            counts[emoji] = (counts[emoji] ?? 0) + 1
        }
        posts[index].reactionsCount = counts
        
        do {
            try await PostService.shared.reactToPost(postId: post.id, type: emoji)
        } catch {
            
            posts[index].currentUserReaction = originalReaction

            Task { await loadFeed() }
        }
    }
}

