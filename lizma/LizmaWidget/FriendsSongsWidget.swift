import SwiftUI
import WidgetKit

/// Friends' latest songs on the home screen (and the lock screen): the covers, who shared them and when.
/// A tap opens the post in Lizma.
struct FriendsSongsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetShared.kind, provider: FriendsSongsProvider()) { entry in
            FriendsSongsView(entry: entry)
        }
        .configurationDisplayName("Friends' songs")
        .description("The latest songs your friends shared.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
        // Covers run to the edges; the layouts below keep their own margins.
        .contentMarginsDisabled()
    }
}

struct FriendsSongsView: View {
    let entry: FriendsSongsEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content
            // The language chosen in the app, not only the phone's.
            .environment(\.locale, Locale(identifier: entry.language))
    }

    @ViewBuilder
    private var content: some View {
        switch entry.content {
        case .signedOut:
            MessageView(symbol: "person.crop.circle", title: "Open Lizma to sign in", detail: nil)
        case .songs(let songs):
            if let first = songs.first {
                switch family {
                case .systemSmall:
                    SmallSongView(song: first, language: entry.language)
                case .accessoryRectangular:
                    LockScreenSongView(song: first)
                case .systemMedium, .systemLarge, .systemExtraLarge:
                    SongTilesView(songs: songs, language: entry.language)
                default:
                    SmallSongView(song: first, language: entry.language)
                }
            } else {
                MessageView(symbol: "music.note", title: "No songs from friends yet",
                            detail: "When friends share a song, it shows up here.")
            }
        }
    }
}

// MARK: - Sizes

/// One song: the cover fills the widget, who and what at the bottom.
private struct SmallSongView: View {
    let song: WidgetSong
    let language: String

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    AvatarView(image: song.avatar, name: song.authorName, size: 22)
                    Text(song.authorName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                }
                Text(song.trackName)
                    .font(.caption)
                    .lineLimit(1)
                    .opacity(0.85)
            }
            .foregroundStyle(.white)
            .padding(12)
        }
        .overlay(alignment: .topTrailing) {
            AgeBadge(date: song.postedAt, language: language)
                .padding(10)
        }
        .containerBackground(for: .widget) {
            CoverView(image: song.cover)
        }
        .widgetURL(song.url)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: "\(song.authorName): \(song.trackName), \(song.artistName)"))
    }
}

/// Two songs side by side (medium) or four in a grid (large), each opening its post.
private struct SongTilesView: View {
    let songs: [WidgetSong]
    let language: String
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if family == .systemMedium {
                HStack(spacing: 8) {
                    tiles(Array(songs.prefix(2)), slots: 2)
                }
            } else {
                let shown = Array(songs.prefix(4))
                VStack(spacing: 8) {
                    HStack(spacing: 8) { tiles(Array(shown.prefix(2)), slots: 2) }
                    HStack(spacing: 8) { tiles(Array(shown.dropFirst(2)), slots: 2) }
                }
            }
        }
        .padding(10)
        .containerBackground(for: .widget) {
            Color(red: 0.07, green: 0.07, blue: 0.08)
        }
    }

    /// The songs, and empty spaces for the rest, so the tiles keep their size.
    @ViewBuilder
    private func tiles(_ row: [WidgetSong], slots: Int) -> some View {
        ForEach(row) { song in
            SongTile(song: song, language: language)
        }
        ForEach(row.count..<max(row.count, slots), id: \.self) { _ in
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(.white.opacity(0.06))
        }
    }
}

private struct SongTile: View {
    let song: WidgetSong
    let language: String

    var body: some View {
        Link(destination: song.url) {
            ZStack(alignment: .bottomLeading) {
                CoverView(image: song.cover)
                LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
                HStack(spacing: 6) {
                    AvatarView(image: song.avatar, name: song.authorName, size: 20)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(song.authorName)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                        Text(song.trackName)
                            .font(.caption2)
                            .lineLimit(1)
                            .opacity(0.85)
                    }
                }
                .foregroundStyle(.white)
                .padding(8)
            }
            .overlay(alignment: .topTrailing) {
                AgeBadge(date: song.postedAt, language: language)
                    .padding(6)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: "\(song.authorName): \(song.trackName), \(song.artistName)"))
    }
}

/// The lock screen: who and what, in the system's tint.
private struct LockScreenSongView: View {
    let song: WidgetSong

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Label {
                Text(song.authorName)
            } icon: {
                Image(systemName: "music.note")
            }
            .font(.headline)
            .widgetAccentable()
            .lineLimit(1)
            Text(song.trackName)
                .font(.caption)
                .lineLimit(1)
            Text(song.artistName)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.clear }
        .widgetURL(song.url)
    }
}

/// Signed out, or nothing shared yet.
private struct MessageView: View {
    let symbol: String
    let title: LocalizedStringKey
    let detail: LocalizedStringKey?
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if family == .accessoryRectangular {
                VStack(alignment: .leading, spacing: 2) {
                    Label("Lizma", systemImage: "music.note").font(.headline).widgetAccentable()
                    Text(title).font(.caption).lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(spacing: 8) {
                    Image(systemName: symbol)
                        .font(.title2)
                        .foregroundStyle(.white.opacity(0.8))
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .multilineTextAlignment(.center)
                    if let detail, family != .systemSmall {
                        Text(detail)
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.white.opacity(0.7))
                    }
                }
                .foregroundStyle(.white)
                .padding()
            }
        }
        .containerBackground(for: .widget) {
            if family == .accessoryRectangular {
                Color.clear
            } else {
                LinearGradient(colors: [Color(red: 0.16, green: 0.12, blue: 0.28), Color(red: 0.05, green: 0.05, blue: 0.07)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
        .widgetURL(URL(string: "lizma://"))
    }
}

// MARK: - Pieces

private struct CoverView: View {
    let image: UIImage?

    var body: some View {
        GeometryReader { proxy in
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: proxy.size.width, height: proxy.size.height)
                    .clipped()
            } else {
                ZStack {
                    LinearGradient(colors: [Color(red: 0.24, green: 0.2, blue: 0.45), Color(red: 0.09, green: 0.08, blue: 0.16)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                    Image(systemName: "music.note")
                        .font(.system(size: min(proxy.size.width, proxy.size.height) * 0.3))
                        .foregroundStyle(.white.opacity(0.35))
                }
            }
        }
    }
}

private struct AvatarView: View {
    let image: UIImage?
    let name: String
    let size: CGFloat

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    Circle().fill(Color(hue: Self.hue(of: name), saturation: 0.55, brightness: 0.85))
                    Text(name.prefix(1).uppercased())
                        .font(.system(size: size * 0.5, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
    }

    /// The same colour for the same name, every time.
    private static func hue(of name: String) -> Double {
        let sum = name.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0xFFFF }
        return Double(sum % 360) / 360
    }
}

/// How long ago it was shared: "5m", "2h", "1w", in the app's language.
private struct AgeBadge: View {
    let date: Date?
    let language: String

    var body: some View {
        if let text = Self.text(for: date, language: language) {
            Text(verbatim: text)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(.black.opacity(0.45), in: Capsule())
        }
    }

    static func text(for date: Date?, language: String, now: Date = .now) -> String? {
        guard let date else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: language)
        // The largest whole unit, as the app shows it on the post ("1 wk. ago"): 13 days is 1w, not 2w (a
        // formatter limited to one unit would round up).
        let units: [Calendar.Component] = [.year, .month, .weekOfMonth, .day, .hour, .minute]
        let parts = calendar.dateComponents(Set(units), from: min(date, now.addingTimeInterval(-60)), to: now)
        guard let unit = units.first(where: { (parts.value(for: $0) ?? 0) > 0 }) else { return nil }
        var largest = DateComponents()
        largest.setValue(parts.value(for: unit), for: unit)
        let formatter = DateComponentsFormatter()
        formatter.calendar = calendar
        // "2h" reads well in English; Ukrainian's abbreviated "2 г" doesn't, its short "2 год" does.
        formatter.unitsStyle = language == "uk" ? .short : .abbreviated
        formatter.allowedUnits = [.year, .month, .weekOfMonth, .day, .hour, .minute]
        return formatter.string(from: largest)
    }
}

// MARK: - Previews

#Preview(as: .systemSmall) {
    FriendsSongsWidget()
} timeline: {
    FriendsSongsEntry.sample
}

#Preview(as: .systemLarge) {
    FriendsSongsWidget()
} timeline: {
    FriendsSongsEntry.sample
    FriendsSongsEntry(date: .now, content: .songs([]), language: "uk")
}
