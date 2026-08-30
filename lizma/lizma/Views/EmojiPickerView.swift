import SwiftUI

struct EmojiPickerView: View {
    let onSelect: (String) -> Void
    @Environment(\.dismiss) var dismiss

    let emojiSections: [(title: String, emojis: [String])] = [
        ("Frequently Used", ["💛", "💗", "🤣", "😋", "🥰", "😍", "🫶", "🥺", "😱", "🔥", "😂"]),
        ("Popular", ["💛", "🫶", "😍", "🔥", "🤣", "💗", "😭", "😋", "🥺", "🥰", "😢", "🤩", "😂", "😱", "😎", "🤭", "😲", "🫠", "❤️‍🔥", "💕", "💘", "❤️", "💖", "💋", "🤍", "💚", "💙", "💜", "🧡", "🖤"]),
        ("All", ["😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"])
    ]
    
    let columns = [
        GridItem(.adaptive(minimum: 44, maximum: 60), spacing: 12)
    ]
    
    @State private var searchText = ""
    
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 20) {
                    ForEach(emojiSections, id: \.title) { section in
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
            .searchable(text: $searchText, prompt: "Search")
            .navigationBarTitleDisplayMode(.inline)
            
            .scrollDismissesKeyboard(.interactively)
            .background(Color(.systemGroupedBackground))
        }
    }
}
