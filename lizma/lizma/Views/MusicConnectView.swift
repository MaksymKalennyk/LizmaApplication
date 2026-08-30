import SwiftUI

struct MusicConnectView: View {
    @StateObject private var viewModel = MusicConnectViewModel()

    var body: some View {
        List {
            Section("STATUS") {
                HStack {
                    Label("Spotify", systemImage: "music.note")
                    Spacer()
                    Image(systemName: viewModel.status.spotifyConnected ? "checkmark.circle.fill" : "xmark.circle")
                        .foregroundStyle(viewModel.status.spotifyConnected ? .green : .secondary)
                }

                HStack {
                    Label("Apple Music", systemImage: "applelogo")
                    Spacer()
                    Image(systemName: viewModel.status.appleConnected ? "checkmark.circle.fill" : "xmark.circle")
                        .foregroundStyle(viewModel.status.appleConnected ? .green : .secondary)
                }
            }

            Section {
                Button {
                    viewModel.connectSpotify()
                } label: {
                    Label("Connect Spotify", systemImage: "music.note")
                }

                Button {
                    viewModel.connectAppleMusic()
                } label: {
                    Label("Connect Apple Music", systemImage: "applelogo")
                }
            } footer: {
                Text("Connect at least one provider to be able to post and listen to full tracks.")
            }

            if let err = viewModel.errorMessage {
                Section {
                    Text(err).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Music Accounts")
        .task { await viewModel.reloadStatus() }
        .refreshable { await viewModel.reloadStatus() }
    }
}

#Preview {
    NavigationStack { MusicConnectView() }
}
