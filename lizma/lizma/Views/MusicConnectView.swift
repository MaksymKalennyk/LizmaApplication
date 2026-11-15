import SwiftUI

struct MusicConnectView: View {
    @StateObject private var viewModel = MusicConnectViewModel()
    @ObservedObject private var musicStatus = MusicStatusStore.shared

    private var spotifyConnected: Bool { musicStatus.status.spotifyConnected }

    var body: some View {
        List {
            Section("STATUS") {
                HStack {
                    Label("Spotify", systemImage: "music.note")
                    Spacer()
                    if !musicStatus.isLoaded {
                        if musicStatus.isLoading {
                            ProgressView()
                        } else {
                            Text("Unknown").foregroundStyle(.secondary)
                        }
                    } else if spotifyConnected {
                        HStack(spacing: 4) {
                            Text("Connected")
                            Image(systemName: "checkmark.circle.fill")
                        }
                        .foregroundStyle(.green)
                    } else {
                        Text("Not connected").foregroundStyle(.secondary)
                    }
                }

                HStack {
                    Label("Apple Music", systemImage: "applelogo")
                    Spacer()
                    Image(systemName: musicStatus.status.appleConnected ? "checkmark.circle.fill" : "xmark.circle")
                        .foregroundStyle(musicStatus.status.appleConnected ? .green : .secondary)
                }
            }

            Section {
                Button {
                    viewModel.connectSpotify()
                } label: {
                    HStack {
                        if spotifyConnected {
                            Label("Reconnect Spotify", systemImage: "arrow.triangle.2.circlepath")
                        } else {
                            Label("Connect Spotify", systemImage: "music.note")
                        }
                        if viewModel.isConnectingSpotify {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(viewModel.isConnectingSpotify)

                Button {
                    viewModel.connectAppleMusic()
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Label("Connect Apple Music", systemImage: "applelogo")
                        Text("Coming soon")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                // Apple Music isn't supported yet.
                .disabled(true)
            } footer: {
                Text("Connect Spotify to search tracks and share them with your friends.")
            }

            if let err = viewModel.errorMessage {
                Section {
                    Text(err).foregroundStyle(.red)
                }
            } else if let statusError = musicStatus.errorMessage {
                Section {
                    Text(statusError).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Music Accounts")
        .task { await musicStatus.refresh() }
        .refreshable { await musicStatus.refresh() }
    }
}

#Preview {
    NavigationStack { MusicConnectView() }
}
