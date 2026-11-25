import SwiftUI

struct PostComposerView: View {
    @Binding var selectedTab: Int
    @ObservedObject private var player = PreviewPlayer.shared

    @State private var text: String = ""
    @State private var searchQuery: String = ""
    @State private var searchResults: [CatalogTrack] = []
    @State private var selectedTrack: CatalogTrack? = nil

    @State private var submitting = false
    @State private var isSearching = false
    /// True once a search finished for the current query (to tell "no results" from "not searched yet").
    @State private var hasSearched = false
    /// The results are the songs a pasted link may be (the server wasn't sure which), not a search.
    @State private var resultsFromLink = false

    @State private var errorMessage: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil

    /// The server's limit for captions.
    private static let maxCaptionLength = 1024

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
                // Nothing to cancel or post until something is picked.
                if hasDraft {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            resetComposer()
                            selectedTab = 0
                        }
                        .disabled(submitting)
                    }
                }
                if selectedTrack != nil {
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            Task { await submit() }
                        } label: {
                            if submitting {
                                ProgressView().controlSize(.regular)
                            } else {
                                Text(String(localized: "composer.publish", defaultValue: "Post", bundle: .app, comment: "The button that publishes the post")).fontWeight(.semibold)
                            }
                        }
                        .disabled(submitting)
                    }
                }
            }
            .onDisappear { player.stop() }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if let track = selectedTrack {
            selectedTrackView(track)
        } else {
            searchView
        }
    }

    private func selectedTrackView(_ track: CatalogTrack) -> some View {
        Form {
            Section {
                HStack(spacing: 12) {
                    artwork(of: track, side: 64, cornerRadius: 8)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(track.trackName)
                            .font(.headline)
                            .lineLimit(2)
                        Text(track.artistName)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)

                    previewButton(for: track)

                    Button {
                        player.stop()
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
                        ContentUnavailableView {
                            Label("Find a track", systemImage: "music.note")
                        } description: {
                            Text("Search for a song to share with your friends.")
                        } actions: {
                            VStack(spacing: 8) {
                                Text("Copied a song link in Apple Music or Spotify?")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                    // The Ukrainian text needs two lines: let it wrap instead of trailing off.
                                    .fixedSize(horizontal: false, vertical: true)
                                PasteButton(payloadType: String.self) { strings in
                                    guard let link = strings.first else { return }
                                    Task { @MainActor in searchQuery = link }
                                }
                                .buttonBorderShape(.capsule)
                                .labelStyle(.titleAndIcon)
                            }
                        }
                    } else if hasSearched {
                        ContentUnavailableView.search(text: trimmedQuery)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            } else {
                Section {
                    resultRows
                } header: {
                    if resultsFromLink {
                        Text("Which one is it?")
                    }
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $searchQuery,
                    placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Search songs and artists")
        .autocorrectionDisabled()
        .onChange(of: searchQuery) { _, newValue in
            scheduleSearch(for: newValue)
        }
    }

    private var resultRows: some View {
        ForEach(searchResults) { track in
            HStack(spacing: 12) {
                Button {
                    selectedTrack = track
                } label: {
                    HStack(spacing: 12) {
                        artwork(of: track, side: 48, cornerRadius: 6)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.trackName)
                                .font(.body)
                                .foregroundStyle(Color.primary)
                                .lineLimit(1)
                            Text(track.artistName)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            // Choosing among the songs a link may be: the album tells versions apart.
                            if resultsFromLink, let album = track.collectionName {
                                Text(album)
                                    .font(.caption)
                                    .foregroundStyle(.tertiary)
                                    .lineLimit(1)
                            }
                        }

                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                previewButton(for: track)
            }
        }
    }

    private var trimmedQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Listen before posting: the 30-second preview, when the catalog has one.
    @ViewBuilder
    private func previewButton(for track: CatalogTrack) -> some View {
        if let url = track.previewUrl.flatMap(URL.init(string:)) {
            let playing = player.isCurrent(url) && player.isPlaying
            Button {
                player.toggle(url)
            } label: {
                Image(systemName: playing ? "pause.circle.fill" : "play.circle.fill")
                    .font(.title2)
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(.tint)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(playing ? "Pause preview" : "Play preview")
        }
    }

    private var artworkPlaceholder: some View {
        ZStack {
            Color(.systemGray5)
            Image(systemName: "music.note").foregroundStyle(.secondary)
        }
    }

    private func artwork(of track: CatalogTrack, side: CGFloat, cornerRadius: CGFloat) -> some View {
        Group {
            if let url = track.artworkURL.flatMap(URL.init(string:)) {
                RemoteImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: { failed in
                    if failed {
                        artworkPlaceholder
                    } else {
                        Color(.systemGray5)
                    }
                }
            } else {
                artworkPlaceholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityHidden(true)
    }

    // MARK: - Actions

    private func scheduleSearch(for query: String) {
        searchTask?.cancel()
        resultsFromLink = false
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            isSearching = false
            hasSearched = false
            errorMessage = nil
            return
        }
        // A pasted link from Apple Music or Spotify names one song: no need to wait for more typing.
        if SharedLinks.containsMusicLink(trimmed) {
            searchTask = Task { await findSong(fromLink: trimmed) }
            return
        }
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            await search(trimmed)
        }
    }

    /// The server finds the song behind the link. When it's sure, the song is picked at once; otherwise the
    /// likeliest ones are listed to choose from.
    private func findSong(fromLink link: String) async {
        isSearching = true
        errorMessage = nil
        searchResults = []
        do {
            let found = try await PostService.shared.resolveLink(link)
            guard !Task.isCancelled else { return }
            let tracks = found.tracks.compactMap(CatalogTrack.init(shared:))
            searchResults = tracks
            hasSearched = true
            if found.matched, let track = tracks.first {
                selectedTrack = track
            } else {
                resultsFromLink = true
            }
        } catch {
            guard !Task.isCancelled, !error.isCancellation else { return }
            // The server's own words: "Only songs can be shared", "Couldn't find this song in Apple Music"…
            errorMessage = error.userMessage
        }
        isSearching = false
    }

    private func search(_ query: String) async {
        isSearching = true
        errorMessage = nil
        do {
            let results = try await CatalogService.shared.search(query)
            guard !Task.isCancelled else { return }
            searchResults = results
            hasSearched = true
        } catch {
            // A newer keystroke cancelled this search: it owns the spinner and the results now.
            guard !Task.isCancelled, !error.isCancellation else { return }
            errorMessage = String(localized: "Search failed: \(error.userMessage)", bundle: .app)
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
            musicId: String(track.trackId),
            musicProvider: "APPLE",
            trackName: String(track.trackName.prefix(255)),
            artistName: String(track.artistName.prefix(255)),
            albumCoverUrl: track.artworkURL,
            caption: caption.isEmpty ? nil : caption,
            previewUrl: track.previewUrl,
            appleMusicUrl: track.appleMusicURL,
            spotifyUrl: track.spotifyUrl
        )
        do {
            _ = try await PostService.shared.createPost(req)
            resetComposer()
            NotificationCenter.default.post(name: .postCreated, object: nil)
            selectedTab = 0
        } catch {
            errorMessage = String(localized: "Couldn't post: \(error.userMessage)", bundle: .app)
        }
    }

    private func resetComposer() {
        searchTask?.cancel()
        player.stop()
        text = ""
        selectedTrack = nil
        searchQuery = ""
        searchResults = []
        isSearching = false
        hasSearched = false
        resultsFromLink = false
        errorMessage = nil
    }
}

#Preview {
    PostComposerView(selectedTab: .constant(2))
}
