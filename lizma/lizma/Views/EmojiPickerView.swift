import SwiftUI

struct EmojiPickerView: View {
    let onSelect: (String) -> Void
    @Environment(\.dismiss) var dismiss

    private static let emojiSections: [(title: String, emojis: [String])] = [
        (String(localized: "Frequently Used", bundle: .app), ["💛", "💗", "🤣", "😋", "🥰", "😍", "🫶", "🥺", "😱", "🔥", "😂"]),
        (String(localized: "Popular", bundle: .app), ["💛", "🫶", "😍", "🔥", "🤣", "💗", "😭", "😋", "🥺", "🥰", "😢", "🤩", "😂", "😱", "😎", "🤭", "😲", "🫠", "❤️‍🔥", "💕", "💘", "❤️", "💖", "💋", "🤍", "💚", "💙", "💜", "🧡", "🖤"]),
        (String(localized: "All", bundle: .app), ["😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"])
    ]

    let columns = [
        GridItem(.adaptive(minimum: 44, maximum: 60), spacing: 12)
    ]

    private struct Cell: Identifiable {
        let id: String
        let emoji: String
    }

    /// The sections overlap, and ids must be unique in the whole grid: with the emoji alone as the id,
    /// every emoji already shown in an earlier section was silently left out of the later ones.
    private func cells(of section: (title: String, emojis: [String])) -> [Cell] {
        section.emojis.enumerated().map { index, emoji in
            Cell(id: "\(section.title)-\(index)", emoji: emoji)
        }
    }

    /// Every other emoji: the phone's emoji keyboard, with its own search, skin tones and recently used.
    private var anyEmojiField: some View {
        HStack(spacing: 10) {
            Image(systemName: "face.smiling")
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            EmojiKeyboardField(placeholder: String(localized: "Any emoji: tap for the emoji keyboard", bundle: .app)) { emoji in
                onSelect(emoji)
                dismiss()
            }
            .frame(height: 22)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(.fill.tertiary, in: Capsule())
        .padding(.horizontal)
        .padding(.top, 8)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    anyEmojiField
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 20) {
                        ForEach(Self.emojiSections, id: \.title) { section in
                            Section(header: Text(section.title)
                                        .font(.headline)
                                        .foregroundColor(.secondary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.top, 8)) {
                                ForEach(cells(of: section)) { cell in
                                    Button {
                                        onSelect(cell.emoji)
                                        dismiss()
                                    } label: {
                                        Text(cell.emoji)
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
            .navigationTitle("React")
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
        }
    }
}
