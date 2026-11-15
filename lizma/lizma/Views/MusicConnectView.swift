import SwiftUI

struct MusicConnectView: View {
    @StateObject private var viewModel = MusicConnectViewModel()
    @ObservedObject private var musicStatus = MusicStatusStore.shared

    private var spotifyConnected: Bool { musicStatus.status.spotifyConnected }

    var body: some View {
        List {
            // One row per service: what it is, its state and the one thing you can do with it.
            Section {
                HStack(spacing: 12) {
                    ServiceIcon(systemName: "music.note", color: .green)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Spotify")
                        spotifyStatus
                            .font(.caption)
                    }

                    Spacer(minLength: 8)

                    if viewModel.isConnectingSpotify {
                        ProgressView()
                    } else if musicStatus.isLoaded {
                        if spotifyConnected {
                            Button("Reconnect") { viewModel.connectSpotify() }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                        } else {
                            Button("Connect") { viewModel.connectSpotify() }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)
                        }
                    }
                }

                HStack(spacing: 12) {
                    ServiceIcon(systemName: "applelogo", color: .gray)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Apple Music")
                        Text("Coming soon")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .opacity(0.6)
            } footer: {
                Text("Connect Spotify to search tracks and share them with your friends.")
            }

            if let err = viewModel.errorMessage ?? musicStatus.errorMessage {
                Section {
                    Text(err).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Music Accounts")
        .navigationBarTitleDisplayMode(.inline)
        .task { await musicStatus.refresh() }
        .refreshable { await musicStatus.refresh() }
    }

    @ViewBuilder
    private var spotifyStatus: some View {
        if !musicStatus.isLoaded {
            Text(musicStatus.isLoading ? "Checking…" : "Status unknown")
                .foregroundStyle(.secondary)
        } else if spotifyConnected {
            // Not a `Label`: in a list row that would pull the separator over to its text.
            HStack(spacing: 4) {
                Image(systemName: "checkmark.circle.fill")
                Text("Connected")
            }
            .foregroundStyle(.green)
            .accessibilityElement(children: .combine)
        } else {
            Text("Not connected")
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    NavigationStack { MusicConnectView() }
}
