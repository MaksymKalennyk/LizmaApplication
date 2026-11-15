import SwiftUI

struct MusicAccountsStatusRow: View {
    @ObservedObject private var musicStatus = MusicStatusStore.shared

    var body: some View {
        NavigationLink {
            MusicConnectView()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "music.quarternote.3")
                    .imageScale(.large)
                    .foregroundStyle(.tint)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Music Accounts").bold()

                    if musicStatus.isLoaded {
                        HStack(spacing: 8) {
                            HStack(spacing: 4) {
                                Image(systemName: musicStatus.status.spotifyConnected ? "checkmark.circle.fill" : "xmark.circle")
                                    .foregroundStyle(musicStatus.status.spotifyConnected ? .green : .secondary)
                                Text("Spotify")
                            }

                            HStack(spacing: 4) {
                                Image(systemName: musicStatus.status.appleConnected ? "checkmark.circle.fill" : "xmark.circle")
                                    .foregroundStyle(musicStatus.status.appleConnected ? .green : .secondary)
                                Text("Apple Music")
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    } else if musicStatus.isLoading {
                        ProgressView().controlSize(.small)
                    }

                    if let err = musicStatus.errorMessage {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }

                Spacer()
                // NavigationLink inside a Form already draws the disclosure chevron.
            }
        }
        .task {
            await musicStatus.refresh()
        }
    }
}
