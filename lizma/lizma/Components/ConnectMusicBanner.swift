import SwiftUI

struct ConnectMusicBanner: View {
    @State private var status = ProvidersStatus(spotifyConnected: false, appleConnected: false)
    @State private var showConnect = false

    var body: some View {
        Group {
            if !(status.spotifyConnected || status.appleConnected) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Connect a music service").font(.headline)
                    Text("Link Spotify or Apple Music to post and listen to full tracks.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button {
                        showConnect = true
                    } label: {
                        Label("Connect now", systemImage: "link")
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
        .sheet(isPresented: $showConnect) {
            NavigationStack { MusicConnectView() }
        }
        .task {
            if let s = try? await MusicService.shared.status() { status = s }
        }
    }
}
