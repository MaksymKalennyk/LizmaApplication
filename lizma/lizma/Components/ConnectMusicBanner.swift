import SwiftUI

struct ConnectMusicBanner: View {
    @ObservedObject private var musicStatus = MusicStatusStore.shared
    @State private var showConnect = false

    var body: some View {
        Group {
            if musicStatus.isLoaded && !musicStatus.status.spotifyConnected {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Connect a music service").font(.headline)
                    Text("Link your Spotify account to search tracks and share them with friends.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button {
                        showConnect = true
                    } label: {
                        Label("Connect Spotify to search tracks", systemImage: "link")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(.tint))
                            .foregroundStyle(.white)
                    }
                }
                .padding()
                .background(RoundedRectangle(cornerRadius: 16).fill(.thinMaterial))
                .padding(.horizontal)
            }
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
        .task {
            await musicStatus.refreshIfNeeded()
        }
    }
}
