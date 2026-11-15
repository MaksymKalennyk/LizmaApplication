import SwiftUI

struct EmojiPickerView: View {
    let onSelect: (String) -> Void
    @Environment(\.dismiss) var dismiss

    private static let emojiSections: [(title: String, emojis: [String])] = [
        ("Frequently Used", ["💛", "💗", "🤣", "😋", "🥰", "😍", "🫶", "🥺", "😱", "🔥", "😂"]),
        ("Popular", ["💛", "🫶", "😍", "🔥", "🤣", "💗", "😭", "😋", "🥺", "🥰", "😢", "🤩", "😂", "😱", "😎", "🤭", "😲", "🫠", "❤️‍🔥", "💕", "💘", "❤️", "💖", "💋", "🤍", "💚", "💙", "💜", "🧡", "🖤"]),
        ("All", ["😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"])
    ]

    /// Every emoji once, in display order (the sections overlap).
    private static let allEmojis: [String] = {
        var seen = Set<String>()
        return emojiSections.flatMap(\.emojis).filter { seen.insert($0).inserted }
    }()

    let columns = [
        GridItem(.adaptive(minimum: 44, maximum: 60), spacing: 12)
    ]

    @State private var searchText = ""

    private var visibleSections: [(title: String, emojis: [String])] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return Self.emojiSections }
        let matches = Self.allEmojis.filter { Self.emoji($0, matches: query) }
        return matches.isEmpty ? [] : [("Results", matches)]
    }

    /// Matches the Unicode names of the emoji's scalars, e.g. "fire" → 🔥, "heart" → ❤️ 💛 💗 …
    private static func emoji(_ emoji: String, matches query: String) -> Bool {
        emoji.unicodeScalars.contains { scalar in
            // Skip joiners/variation selectors, whose names ("ZERO WIDTH JOINER") would match nonsense.
            scalar.properties.isEmoji
                && (scalar.properties.name?.localizedCaseInsensitiveContains(query) ?? false)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if visibleSections.isEmpty {
                    ContentUnavailableView.search(text: searchText)
                        .padding(.top, 40)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 20) {
                        ForEach(visibleSections, id: \.title) { section in
                            Section(header: Text(section.title)
                                        .font(.headline)
                                        .foregroundColor(.secondary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.top, 8)) {
                                ForEach(section.emojis, id: \.self) { emoji in
                                    Button {
                                        onSelect(emoji)
                                        dismiss()
                                    } label: {
                                        Text(emoji)
                                            .font(.system(size: 36))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
            .searchable(text: $searchText, prompt: "Search")
            .navigationBarTitleDisplayMode(.inline)

            .scrollDismissesKeyboard(.interactively)
            .background(Color(.systemGroupedBackground))
        }
    }
}
