import SwiftUI
import UIKit

/// Downloads artwork once and keeps it in memory. Views that show the same picture (a post's cover and
/// its blurred backdrop) share one download, and a download keeps going when the view that asked for it
/// scrolls away.
@MainActor
final class ImageStore {
    static let shared = ImageStore()

    private let cache = NSCache<NSURL, UIImage>()
    private var downloads: [URL: Task<UIImage?, Never>] = [:]

    private init() {
        cache.countLimit = 150
    }

    func cachedImage(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    /// The image, or nil when it couldn't be loaded. Failures aren't remembered: the next call tries again.
    func image(for url: URL) async -> UIImage? {
        if let cached = cachedImage(for: url) { return cached }
        if let running = downloads[url] { return await running.value }

        let download = Task<UIImage?, Never> {
            guard let (data, response) = try? await URLSession.shared.data(from: url) else { return nil }
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) { return nil }
            guard let image = UIImage(data: data) else { return nil }
            // Decode off the main thread, so the first frame with the picture doesn't stutter.
            return await image.byPreparingForDisplay() ?? image
        }
        downloads[url] = download
        let image = await download.value
        downloads[url] = nil
        if let image {
            cache.setObject(image, forKey: url as NSURL)
        }
        return image
    }
}

/// A remote picture with a placeholder. Used instead of `AsyncImage`, which treats a load cancelled by
/// scrolling or by a list reload as a failure and never tries again, leaving covers blank.
struct RemoteImage<Content: View, Placeholder: View>: View {
    private let url: URL?
    private let content: (Image) -> Content
    private let placeholder: (_ failed: Bool) -> Placeholder

    @State private var image: UIImage?
    @State private var failed = false

    /// - Parameters:
    ///   - content: how to show the loaded picture.
    ///   - placeholder: shown while loading (`failed == false`) and when the picture can't be loaded.
    init(url: URL?,
         @ViewBuilder content: @escaping (Image) -> Content,
         @ViewBuilder placeholder: @escaping (_ failed: Bool) -> Placeholder) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
        // A picture seen before is there from the first frame, without a flash of the placeholder.
        _image = State(initialValue: url.flatMap { ImageStore.shared.cachedImage(for: $0) })
    }

    var body: some View {
        ZStack {
            if let image {
                content(Image(uiImage: image))
                    .transition(.opacity)
            } else {
                placeholder(failed)
            }
        }
        // Runs again whenever the view comes back on screen, so a failed load is retried.
        .task(id: url) {
            await load()
        }
    }

    private func load() async {
        guard let url else {
            image = nil
            failed = true
            return
        }
        if let cached = ImageStore.shared.cachedImage(for: url) {
            image = cached
            failed = false
            return
        }
        image = nil
        failed = false
        let loaded = await ImageStore.shared.image(for: url)
        guard !Task.isCancelled else { return }
        if let loaded {
            withAnimation(.easeOut(duration: 0.25)) { image = loaded }
        } else {
            failed = true
        }
    }
}
