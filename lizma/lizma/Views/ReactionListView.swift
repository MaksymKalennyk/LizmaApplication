import SwiftUI

struct ReactionListView: View {
    let postId: Int
    @State private var reactions: [ReactionDetailResponse] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationView {
            Group {
                if isLoading && reactions.isEmpty {
                    ProgressView()
                } else if let errorMessage = errorMessage {
                    VStack {
                        Text(errorMessage).foregroundColor(.red)
                        Button("Retry") {
                            Task { await loadReactions() }
                        }
                    }
                } else if reactions.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "heart.slash")
                            .font(.system(size: 40))
                            .foregroundColor(.gray)
                        Text("No reactions yet.")
                            .foregroundColor(.secondary)
                    }
                } else {
                    List(reactions) { reaction in
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 40, height: 40)
                                .overlay(
                                    Text(String(reaction.authorUsername.prefix(1).uppercased()))
                                        .foregroundColor(.primary)
                                        .bold()
                                )
                            
                            Text(reaction.authorUsername)
                                .font(.body)
                                .bold()
                            
                            Spacer()
                            
                            Text(reaction.type)
                                .font(.system(size: 24))
                        }
                        .padding(.vertical, 4)
                    }
                    .listStyle(.plain)
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
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
