import SwiftUI

struct MusicAccountsStatusRow: View {
    @State private var status = ProvidersStatus(spotifyConnected: false, appleConnected: false)
    @State private var err: String? = nil

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

                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Image(systemName: status.spotifyConnected ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(status.spotifyConnected ? .green : .secondary)
                            Text("Spotify")
                        }

                        HStack(spacing: 4) {
                            Image(systemName: status.appleConnected ? "checkmark.circle.fill" : "xmark.circle")
                                .foregroundStyle(status.appleConnected ? .green : .secondary)
                            Text("Apple Music")
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundStyle(Color(.tertiaryLabel))
            }
        }
        .task {
            do {
                status = try await MusicService.shared.status()
            } catch {
                err = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
