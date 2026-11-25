import SwiftUI

/// The share sheet: the song the link points at (or a choice of the likeliest ones), a caption, and Post, laid out
/// like the app's own composer.
struct ShareView: View {
    @ObservedObject var model: ShareModel

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(Text(verbatim: "Lizma"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { model.cancel() }
                            .disabled(isPosting)
                    }
                    if canPost {
                        ToolbarItem(placement: .confirmationAction) {
                            Button {
                                Task { await model.post() }
                            } label: {
                                if isPosting {
                                    ProgressView()
                                } else {
                                    Text("Post").fontWeight(.semibold)
                                }
                            }
                            .disabled(isPosting)
                        }
                    }
                }
        }
        // The language chosen in the app, not only the phone's.
        .environment(\.locale, Locale(identifier: ShareAPI.language))
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .finding:
            VStack(spacing: 12) {
                ProgressView()
                Text("Finding the song…")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .signedOut:
            ContentUnavailableView {
                Label("Open Lizma to sign in", systemImage: "person.crop.circle")
            } description: {
                Text("Then share the song again.")
            }

        case .failed(let message, let canRetry):
            ContentUnavailableView {
                Label {
                    Text(verbatim: message)
                } icon: {
                    Image(systemName: "music.note")
                }
            } actions: {
                if canRetry {
                    Button("Try again") {
                        Task { await model.find() }
                    }
                }
            }

        case .choosing(let tracks):
            List {
                Section {
                    ForEach(tracks) { track in
                        Button {
                            model.choose(track)
                        } label: {
                            SongRow(track: track, coverSide: 48)
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Which one is it?")
                }
            }

        case .ready(let track, let choices), .posting(let track, let choices):
            Form {
                Section {
                    SongRow(track: track, coverSide: 64)
                    if !choices.isEmpty {
                        Button("Choose another song") { model.chooseAnother() }
                            .disabled(isPosting)
                    }
                }
                Section {
                    TextField("Add a caption (optional)", text: $model.caption, axis: .vertical)
                        .lineLimit(3...8)
                        .disabled(isPosting)
                        .onChange(of: model.caption) { _, newValue in
                            if newValue.count > ShareModel.maxCaptionLength {
                                model.caption = String(newValue.prefix(ShareModel.maxCaptionLength))
                            }
                        }
                } footer: {
                    if let error = model.postError {
                        Text(verbatim: error)
                            .foregroundStyle(.red)
                    } else if model.caption.count > ShareModel.maxCaptionLength - 100 {
                        // Only once the limit is in sight.
                        Text(verbatim: "\(model.caption.count)/\(ShareModel.maxCaptionLength)")
                    }
                }
            }

        case .posted:
            VStack(spacing: 12) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 52))
                    .foregroundStyle(.green)
                Text("Posted")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .combine)
        }
    }

    private var canPost: Bool {
        switch model.phase {
        case .ready, .posting: return true
        default: return false
        }
    }

    private var isPosting: Bool {
        if case .posting = model.phase { return true }
        return false
    }
}

/// A song: its cover, title, artist and album (which tells versions apart when choosing).
private struct SongRow: View {
    let track: SharedTrack
    let coverSide: CGFloat

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: track.albumCoverUrl.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().aspectRatio(contentMode: .fill)
                } else {
                    ZStack {
                        Color(.systemGray5)
                        Image(systemName: "music.note").foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: coverSide, height: coverSide)
            .clipShape(RoundedRectangle(cornerRadius: coverSide > 50 ? 8 : 6, style: .continuous))
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: track.trackName)
                    .font(coverSide > 50 ? .headline : .body)
                    .foregroundStyle(Color.primary)
                    .lineLimit(2)
                Text(verbatim: track.artistName)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if let album = track.albumName {
                    Text(verbatim: album)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
