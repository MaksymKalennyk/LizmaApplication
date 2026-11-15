import SwiftUI

/// Shown instead of the composer while no music service is connected: tracks come from Spotify
/// search, so there is nothing to post without it.
struct ConnectMusicPrompt: View {
    @ObservedObject private var musicStatus = MusicStatusStore.shared
    @State private var showConnect = false

    var body: some View {
        ContentUnavailableView {
            Label("Connect Spotify to post", systemImage: "music.note.list")
        } description: {
            Text("A post is a track you pick from Spotify. Connect your account to search for one.")
        } actions: {
            Button {
                showConnect = true
            } label: {
                Text("Connect Spotify")
                    .frame(minWidth: 160)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .sheet(isPresented: $showConnect, onDismiss: {
            Task { await musicStatus.refresh() }
        }) {
            NavigationStack {
                MusicConnectView()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") { showConnect = false }
                        }
                    }
            }
        }
    }
}
