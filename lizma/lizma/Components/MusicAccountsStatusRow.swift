import SwiftUI

/// The Profile row that opens "Music Accounts" and says in one line what is connected.
struct MusicAccountsStatusRow: View {
    @ObservedObject private var musicStatus = MusicStatusStore.shared

    var body: some View {
        NavigationLink {
            MusicConnectView()
        } label: {
            // A `Label`, like the other Profile rows, so the icons and the texts line up with them.
            // (NavigationLink inside a Form already draws the disclosure chevron.)
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Music Accounts")

                    if let err = musicStatus.errorMessage {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                    } else if musicStatus.isLoaded {
                        Text(summary)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if musicStatus.isLoading {
                        ProgressView().controlSize(.small)
                    }
                }
            } icon: {
                Image(systemName: "music.note")
            }
        }
        .task {
            await musicStatus.refresh()
        }
    }

    private var summary: String {
        var connected: [String] = []
        if musicStatus.status.spotifyConnected { connected.append("Spotify") }
        if musicStatus.status.appleConnected { connected.append("Apple Music") }
        return connected.isEmpty ? "Not connected" : connected.joined(separator: ", ") + " connected"
    }
}

/// A Settings-style icon: a white symbol on a rounded coloured square.
struct ServiceIcon: View {
    let systemName: String
    let color: Color
    var size: CGFloat = 30

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
            .fill(color.gradient)
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: systemName)
                    .font(.system(size: size * 0.5, weight: .semibold))
                    .foregroundStyle(.white)
            )
            .accessibilityHidden(true)
    }
}
