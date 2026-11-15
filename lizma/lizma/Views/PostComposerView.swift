import SwiftUI

struct PostComposerView: View {
    @Binding var selectedTab: Int
    @ObservedObject private var musicStatus = MusicStatusStore.shared

    @State private var text: String = ""
    @State private var searchQuery: String = ""
    @State private var searchResults: [SpotifyTrack] = []
    @State private var selectedTrack: SpotifyTrack? = nil

    @State private var submitting = false
    @State private var isSearching = false
    /// True once a search finished for the current query (to tell "no results" from "not searched yet").
    @State private var hasSearched = false

    @State private var errorMessage: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                content

                if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                }
            }
            .navigationTitle("New post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        resetComposer()
                        selectedTab = 0
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await submit() }
                    } label: {
                        if submitting {
                            ProgressView().controlSize(.regular)
                        } else {
                            Text("Post")
                        }
                    }
                    .disabled(submitting || selectedTrack == nil)
                }
            }
            .task {
                await musicStatus.refresh()
            }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if !musicStatus.isLoaded {
            if let statusError = musicStatus.errorMessage, !musicStatus.isLoading {
                statusErrorView(statusError)
            } else {
                // Don't guess: no banner or search until we know whether Spotify is connected.
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else if !musicStatus.status.spotifyConnected {
            // Tracks come from Spotify search, so there is nothing to post without it.
            VStack {
                ConnectMusicBanner()
                    .padding(.top)
                Spacer()
            }
        } else if let track = selectedTrack {
            selectedTrackView(track)
        } else {
            searchView
        }
    }

    private func statusErrorView(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 36))
                .foregroundStyle(.secondary)
            Text("Couldn't check your music accounts")
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Retry") {
                Task { await musicStatus.refresh() }
            }
            .buttonStyle(.borderedProminent)
        }
        .multilineTextAlignment(.center)
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func selectedTrackView(_ track: SpotifyTrack) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    if let urlString = track.album.images?.first?.url, let url = URL(string: urlString) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
                        .frame(width: 80, height: 80)
                        .cornerRadius(8)
                    } else {
                        Rectangle().fill(Color.gray.opacity(0.3)).frame(width: 80, height: 80).cornerRadius(8)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(track.name).font(.headline).lineLimit(2)
                        Text(track.artists.map { $0.name }.joined(separator: ", "))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    Button {
                        selectedTrack = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel("Remove selected track")
                }
                .padding()
                .background(RoundedRectangle(cornerRadius: 12).fill(.thinMaterial))

                TextField("Add a caption (optional)", text: $text, axis: .vertical)
                    .lineLimit(3...8)
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(.thinMaterial))
            }
            .padding()
        }
    }

    private var searchView: some View {
        VStack {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(.gray)
                TextField("Search for a track...", text: $searchQuery)
                    .autocorrectionDisabled()
                    .onChange(of: searchQuery) { _, newValue in
                        scheduleSearch(for: newValue)
                    }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(.systemGray6)))
            .padding()

            if isSearching {
                ProgressView().padding()
            }

            if searchResults.isEmpty && !isSearching {
                let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty {
                    ContentUnavailableView(
                        "Find a track",
                        systemImage: "music.note",
                        description: Text("Search Spotify for a song to share with your friends.")
                    )
                } else if hasSearched {
                    ContentUnavailableView.search(text: trimmed)
                } else {
                    Spacer()
                }
            } else {
                List(searchResults) { track in
                    Button {
                        selectedTrack = track
                    } label: {
                        HStack {
                            if let urlString = track.album.images?.first?.url, let url = URL(string: urlString) {
                                AsyncImage(url: url) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.gray.opacity(0.3)
                                }
                                .frame(width: 50, height: 50)
                                .cornerRadius(4)
                            } else {
                                Rectangle().fill(Color.gray.opacity(0.3)).frame(width: 50, height: 50).cornerRadius(4)
                            }

                            VStack(alignment: .leading) {
                                Text(track.name).font(.headline).foregroundColor(.primary)
                                Text(track.artists.map { $0.name }.joined(separator: ", "))
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    // MARK: - Actions

    private func scheduleSearch(for query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            isSearching = false
            hasSearched = false
            errorMessage = nil
            return
        }
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            await search(trimmed)
        }
    }

    private func search(_ query: String) async {
        isSearching = true
        errorMessage = nil
        do {
            let results = try await MusicService.shared.searchSpotify(query: query)
            guard !Task.isCancelled else { return }
            searchResults = results
            hasSearched = true
        } catch {
            // A newer keystroke cancelled this search: it owns the spinner and the results now.
            guard !Task.isCancelled, !error.isCancellation else { return }
            errorMessage = "Search failed: \(error.userMessage)"
            if case APIError.server(409, _) = error {
                // e.g. "Spotify account is not connected": show the connect prompt instead.
                await musicStatus.refresh()
            }
        }
        isSearching = false
    }

    private func submit() async {
        guard !submitting, let track = selectedTrack else { return }
        submitting = true
        defer { submitting = false }
        errorMessage = nil

        let caption = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let req = PostRequest(
            musicId: track.id,
            musicProvider: "SPOTIFY",
            trackName: String(track.name.prefix(255)),
            artistName: String(track.artists.map { $0.name }.joined(separator: ", ").prefix(255)),
            albumCoverUrl: track.album.images?.first?.url,
            caption: caption.isEmpty ? nil : caption
        )
        do {
            _ = try await PostService.shared.createPost(req)
            resetComposer()
            NotificationCenter.default.post(name: .postCreated, object: nil)
            selectedTab = 0
        } catch {
            errorMessage = "Failed to post: \(error.userMessage)"
        }
    }

    private func resetComposer() {
        searchTask?.cancel()
        text = ""
        selectedTrack = nil
        searchQuery = ""
        searchResults = []
        isSearching = false
        hasSearched = false
        errorMessage = nil
    }
}

#Preview {
    PostComposerView(selectedTab: .constant(2))
}
