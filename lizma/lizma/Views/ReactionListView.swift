import SwiftUI

struct ReactionListView: View {
    let postId: Int
    @State private var reactions: [ReactionDetailResponse] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && reactions.isEmpty {
                    ProgressView()
                } else if let errorMessage {
                    ContentUnavailableView {
                        Label("Couldn't load reactions", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button("Retry") { Task { await loadReactions() } }
                            .buttonStyle(.bordered)
                    }
                } else if reactions.isEmpty {
                    ContentUnavailableView(
                        "No reactions yet",
                        systemImage: "heart",
                        description: Text("Reactions from your friends will show up here.")
                    )
                } else {
                    List(reactions) { reaction in
                        NavigationLink {
                            UserProfileScreen(username: reaction.authorUsername)
                        } label: {
                            HStack(spacing: 12) {
                                UserLabel(username: reaction.authorUsername, displayName: reaction.authorDisplayName,
                                          avatarUrl: reaction.authorAvatarUrl, size: 40)

                                Spacer(minLength: 8)

                                Text(reaction.type)
                                    .font(.system(size: 24))
                            }
                        }
                        .padding(.vertical, 2)
                        .listRowBackground(Color.clear)
                    }
                    .listStyle(.plain)
                    // Lets the sheet's own background show, like the comments sheet.
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Reactions")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await loadReactions()
            }
        }
    }

    private func loadReactions() async {
        isLoading = true
        errorMessage = nil
        do {
            reactions = try await PostService.shared.getReactions(postId: postId)
        } catch {
            if !error.isCancellation {
                errorMessage = error.userMessage
            }
        }
        isLoading = false
    }
}
