import PhotosUI
import SwiftUI

/// Up to three screenshots for a report or feedback: picked from Photos, shown as thumbnails that can be removed.
/// They're uploaded when the form is sent (`AttachmentService.upload`).
struct ScreenshotPicker: View {
    @Binding var images: [UIImage]

    static let limit = 3

    @State private var selection: [PhotosPickerItem] = []
    @State private var isLoading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !images.isEmpty || isLoading {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(images.enumerated()), id: \.offset) { index, image in
                            thumbnail(image, at: index)
                        }
                        if isLoading {
                            ProgressView()
                                .frame(width: 72, height: 96)
                        }
                    }
                    .padding(.top, 6) // room for the remove buttons
                }
            }
            if images.count < Self.limit {
                PhotosPicker(selection: $selection, maxSelectionCount: Self.limit - images.count,
                             selectionBehavior: .ordered, matching: .images) {
                    Label(images.isEmpty ? "Add screenshots" : "Add more", systemImage: "photo.badge.plus")
                }
                .disabled(isLoading)
            }
        }
        .onChange(of: selection) { _, items in
            guard !items.isEmpty else { return }
            Task { await load(items) }
        }
    }

    private func thumbnail(_ image: UIImage, at index: Int) -> some View {
        Image(uiImage: image)
            .resizable()
            .scaledToFill()
            .frame(width: 72, height: 96)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(alignment: .topTrailing) {
                Button {
                    images.remove(at: index)
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, .black.opacity(0.6))
                }
                .buttonStyle(.plain)
                .offset(x: 6, y: -6)
                .accessibilityLabel("Remove screenshot")
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Screenshot \(index + 1)")
    }

    private func load(_ items: [PhotosPickerItem]) async {
        isLoading = true
        defer {
            isLoading = false
            selection = []
        }
        for item in items {
            guard images.count < Self.limit else { break }
            if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                images.append(image)
            }
        }
    }
}

/// Screenshots attached to a report or feedback, for moderators: a strip of thumbnails that opens them full screen.
struct ScreenshotStrip: View {
    let paths: [String]

    @State private var opened: OpenedScreenshot?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(paths.enumerated()), id: \.offset) { index, path in
                    Button {
                        opened = OpenedScreenshot(index: index)
                    } label: {
                        RemoteImage(url: AppConfig.mediaURL(path), authorized: true) { image in
                            image.resizable().scaledToFill()
                        } placeholder: { failed in
                            ZStack {
                                Color.secondary.opacity(0.15)
                                if failed {
                                    Image(systemName: "photo").foregroundStyle(.secondary)
                                } else {
                                    ProgressView()
                                }
                            }
                        }
                        .frame(width: 72, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Screenshot \(index + 1)")
                }
            }
        }
        .fullScreenCover(item: $opened) { screenshot in
            ScreenshotViewer(paths: paths, startIndex: screenshot.index)
        }
    }

    private struct OpenedScreenshot: Identifiable {
        let index: Int
        var id: Int { index }
    }
}

/// The screenshots full screen, one per page.
private struct ScreenshotViewer: View {
    let paths: [String]
    @State var startIndex: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            TabView(selection: $startIndex) {
                ForEach(Array(paths.enumerated()), id: \.offset) { index, path in
                    RemoteImage(url: AppConfig.mediaURL(path), authorized: true) { image in
                        image.resizable().scaledToFit()
                    } placeholder: { failed in
                        if failed {
                            Image(systemName: "photo").foregroundStyle(.secondary)
                        } else {
                            ProgressView().tint(.white)
                        }
                    }
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: paths.count > 1 ? .always : .never))

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(.black.opacity(0.35), in: Circle())
            }
            .padding(.leading, 12)
            .padding(.top, 4)
            .accessibilityLabel("Close")
        }
        .preferredColorScheme(.dark)
    }
}
