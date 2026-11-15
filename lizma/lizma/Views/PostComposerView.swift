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

    /// The server's limit for captions.
    private static let maxCaptionLength = 1024

    private var isConnected: Bool {
        musicStatus.isLoaded && musicStatus.status.spotifyConnected
    }

    /// Something the user would lose by leaving: a picked track, a caption or a search.
    private var hasDraft: Bool {
        selectedTrack != nil || !text.isEmpty || !searchQuery.isEmpty
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                content

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                }
            }
            .navigationTitle("New post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // Nothing to cancel or post until Spotify is connected and something is picked.
                if isConnected && hasDraft {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            resetComposer()
                            selectedTab = 0
                        }
                        .disabled(submitting)
                    }
                }
                if isConnected && selectedTrack != nil {
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            Task { await submit() }
                        } label: {
                            if submitting {
                                ProgressView().controlSize(.regular)
                            } else {
                                Text("Post").fontWeight(.semibold)
                            }
                        }
                        .disabled(submitting)
                    }
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
            ConnectMusicPrompt()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
        Form {
            Section {
                HStack(spacing: 12) {
                    artwork(of: track, side: 64, cornerRadius: 8)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(track.name)
                            .font(.headline)
                            .lineLimit(2)
                        Text(artistNames(of: track))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)

                    Button {
                        selectedTrack = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(.tertiary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Choose another track")
                }
                .padding(.vertical, 4)
            }

            Section {
                TextField("Add a caption (optional)", text: $text, axis: .vertical)
                    .lineLimit(3...8)
                    .disabled(submitting)
                    .onChange(of: text) { _, newValue in
                        if newValue.count > Self.maxCaptionLength {
                            text = String(newValue.prefix(Self.maxCaptionLength))
                        }
                    }
            } footer: {
                // Only once the limit is in sight.
                if text.count > Self.maxCaptionLength - 100 {
                    Text("\(text.count)/\(Self.maxCaptionLength)")
                }
            }
        }
    }

    private var searchView: some View {
        List {
            if searchResults.isEmpty {
                Group {
                    if isSearching {
                        ProgressView()
                    } else if trimmedQuery.isEmpty {
                        ContentUnavailableView(
                            "Find a track",
                            systemImage: "music.note",
                            description: Text("Search Spotify for a song to share with your friends.")
                        )
                    } else if hasSearched {
                        ContentUnavailableView.search(text: trimmedQuery)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            } else {
                ForEach(searchResults) { track in
                    Button {
                        selectedTrack = track
                    } label: {
                        HStack(spacing: 12) {
                            artwork(of: track, side: 48, cornerRadius: 6)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(track.name)
                                    .font(.body)
                                    .foregroundStyle(Color.primary)
                                    .lineLimit(1)
                                Text(artistNames(of: track))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }

                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $searchQuery,
                    placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Search Spotify")
        .autocorrectionDisabled()
        .onChange(of: searchQuery) { _, newValue in
            scheduleSearch(for: newValue)
        }
    }

    private var trimmedQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func artistNames(of track: SpotifyTrack) -> String {
        track.artists.map { $0.name }.joined(separator: ", ")
    }

    private func artwork(of track: SpotifyTrack, side: CGFloat, cornerRadius: CGFloat) -> some View {
        Group {
            if let urlString = track.album.images?.first?.url, let url = URL(string: urlString) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color(.systemGray5)
                }
            } else {
                ZStack {
                    Color(.systemGray5)
                    Image(systemName: "music.note").foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityHidden(true)
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
            artistName: String(artistNames(of: track).prefix(255)),
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
