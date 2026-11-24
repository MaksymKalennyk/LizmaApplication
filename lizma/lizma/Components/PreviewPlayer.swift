import AVFoundation
import Combine

/// Plays one 30-second preview at a time (in the composer and in a post's player). Starting another stops
/// the one before.
@MainActor
final class PreviewPlayer: ObservableObject {
    static let shared = PreviewPlayer()

    /// What is playing (or paused); nil when stopped.
    @Published private(set) var url: URL?
    @Published private(set) var isPlaying = false
    /// 0…1 of the preview.
    @Published private(set) var progress: Double = 0

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: AnyCancellable?

    private init() {
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
                                                      queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self, let duration = self.player.currentItem?.duration.seconds,
                      duration.isFinite, duration > 0 else { return }
                self.progress = min(1, time.seconds / duration)
            }
        }
    }

    func isCurrent(_ url: URL?) -> Bool {
        url != nil && url == self.url
    }

    /// Plays `url`, or pauses/resumes it if it is the one already loaded.
    func toggle(_ url: URL) {
        if url == self.url {
            if isPlaying {
                player.pause()
                isPlaying = false
            } else {
                if progress >= 1 { player.seek(to: .zero) }
                player.play()
                isPlaying = true
            }
            return
        }
        // Previews play out loud even in silent mode, like any music app.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
        let item = AVPlayerItem(url: url)
        endObserver = NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.isPlaying = false
                    self?.progress = 1
                }
            }
        player.replaceCurrentItem(with: item)
        self.url = url
        progress = 0
        player.play()
        isPlaying = true
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        endObserver = nil
        url = nil
        isPlaying = false
        progress = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
