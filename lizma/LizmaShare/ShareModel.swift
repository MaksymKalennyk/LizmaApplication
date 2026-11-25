import Foundation

/// What the share sheet shows and does: finds the song behind the shared link, lets the user pick one when the
/// server isn't sure, and posts it.
@MainActor
final class ShareModel: ObservableObject {

    enum Phase: Equatable {
        /// Asking the server which song the link is.
        case finding
        /// No sign-in to post with: the app hasn't been opened yet, or its sign-in ended.
        case signedOut
        /// The link isn't a song, it couldn't be found, or the server couldn't be reached; `canRetry` for the latter.
        case failed(message: String, canRetry: Bool)
        /// The server isn't sure: these are the likeliest songs.
        case choosing([SharedTrack])
        /// Ready to post; `choices` are the songs it was picked from, to go back to.
        case ready(SharedTrack, choices: [SharedTrack])
        case posting(SharedTrack, choices: [SharedTrack])
        case posted
    }

    @Published private(set) var phase: Phase = .finding
    @Published var caption = ""
    /// Why the last attempt to post failed, shown under the caption.
    @Published private(set) var postError: String?

    /// Closes the sheet; true once the song is posted.
    var finish: (Bool) -> Void = { _ in }

    /// The server's limit for captions.
    static let maxCaptionLength = 1024

    private var sharedText: String?

    func start(with sharedText: String?) async {
        self.sharedText = sharedText
        await find()
    }

    func find() async {
        guard let text = sharedText, !text.isEmpty else {
            phase = .failed(message: String(localized: "Share a song from Apple Music or Spotify", bundle: .share), canRetry: false)
            return
        }
        guard let api = ShareAPI.current() else {
            phase = .signedOut
            return
        }
        phase = .finding
        do {
            let found = try await api.resolve(text)
            if found.matched, let track = found.tracks.first {
                phase = .ready(track, choices: [])
            } else if found.tracks.isEmpty {
                phase = .failed(message: String(localized: "Couldn't find this song", bundle: .share), canRetry: false)
            } else {
                phase = .choosing(found.tracks)
            }
        } catch ShareAPI.Failure.signedOut {
            ShareKey.current = nil
            phase = .signedOut
        } catch ShareAPI.Failure.message(let message) {
            phase = .failed(message: message, canRetry: false)
        } catch {
            phase = .failed(message: Self.offlineMessage, canRetry: true)
        }
    }

    func choose(_ track: SharedTrack) {
        guard case .choosing(let choices) = phase else { return }
        phase = .ready(track, choices: choices)
    }

    func chooseAnother() {
        guard case .ready(_, let choices) = phase, !choices.isEmpty else { return }
        postError = nil
        phase = .choosing(choices)
    }

    func post() async {
        guard case .ready(let track, let choices) = phase else { return }
        guard let api = ShareAPI.current() else {
            phase = .signedOut
            return
        }
        postError = nil
        phase = .posting(track, choices: choices)
        do {
            try await api.post(track, caption: caption)
            phase = .posted
            // A moment to see it worked, then back to the app the song came from.
            try? await Task.sleep(for: .milliseconds(800))
            finish(true)
        } catch ShareAPI.Failure.signedOut {
            ShareKey.current = nil
            phase = .signedOut
        } catch ShareAPI.Failure.message(let message) {
            postError = message
            phase = .ready(track, choices: choices)
        } catch {
            postError = Self.offlineMessage
            phase = .ready(track, choices: choices)
        }
    }

    func cancel() {
        finish(false)
    }

    private static var offlineMessage: String {
        String(localized: "Couldn't reach Lizma. Check your connection and try again.", bundle: .share)
    }
}
