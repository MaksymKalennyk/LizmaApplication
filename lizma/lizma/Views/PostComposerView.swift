import SwiftUI

struct PostComposerView: View {
    @State private var text: String = ""
    @State private var searchQuery: String = ""
    @State private var searchResults: [SpotifyTrack] = []
    @State private var selectedTrack: SpotifyTrack? = nil
    
    @State private var submitting = false
    @State private var isSearching = false
    @State private var showConnect = false
    @State private var status = ProvidersStatus(spotifyConnected: false, appleConnected: false)

    @State private var errorMessage: String? = nil
    
    @Binding var selectedTab: Int
    @State private var searchTask: Task<Void, Never>? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !(status.spotifyConnected || status.appleConnected) {
                    ConnectMusicBanner()
                        .padding()
                }

                if let track = selectedTrack {
                    
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
                } else {
                    
                    VStack {
                        HStack {
                            Image(systemName: "magnifyingglass").foregroundColor(.gray)
                            TextField("Search for a track...", text: $searchQuery)
                                .autocorrectionDisabled()
                                .onChange(of: searchQuery) { newValue in
                                    searchTask?.cancel()
                                    searchTask = Task {
                                        do {
                                            try await Task.sleep(nanoseconds: 500_000_000) 
                                        } catch { return }
                                        if !Task.isCancelled {
                                            await search()
                                        }
                                    }
                                }
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color(.systemGray6)))
                        .padding()

                        if isSearching {
                            ProgressView().padding()
                        }
                        
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

                if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .padding()
                }
            }
            .navigationTitle("New post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        text = ""
                        selectedTrack = nil
                        searchQuery = ""
                        searchResults = []
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
                do {
                    status = try await MusicService.shared.status()
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
            .sheet(isPresented: $showConnect) {
                NavigationStack { MusicConnectView() }
            }
        }
    }


    private func search() async {
        guard !searchQuery.isEmpty else { return }
        isSearching = true
        errorMessage = nil
        do {
            searchResults = try await MusicService.shared.searchSpotify(query: searchQuery)
        } catch {
            errorMessage = "Search failed: \(error.localizedDescription)"
        }
        isSearching = false
    }

    private func submit() async {
        guard let track = selectedTrack else { return }
        
        do {
            let s = try await MusicService.shared.status()
            if !(s.spotifyConnected || s.appleConnected) {
                showConnect = true
                return
            }
        } catch {
            errorMessage = error.localizedDescription
            return
        }

        submitting = true
        defer { submitting = false }

        do {
            let req = PostRequest(
                musicId: track.id, 
                musicProvider: "SPOTIFY",
                trackName: track.name,
                artistName: track.artists.map { $0.name }.joined(separator: ", "),
                albumCoverUrl: track.album.images?.first?.url,
                caption: text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : text.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            let _ = try await PostService.shared.createPost(req)

            text = ""
            selectedTrack = nil
            searchQuery = ""
            searchResults = []
            selectedTab = 0
            
        } catch {
            self.errorMessage = "Failed to post: \(error.localizedDescription)"
        }
    }
}

#Preview {
    PostComposerView(selectedTab: .constant(2))
}
