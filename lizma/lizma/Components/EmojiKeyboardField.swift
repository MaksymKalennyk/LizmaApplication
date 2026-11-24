import SwiftUI
import UIKit

/// A field that opens straight on the phone's emoji keyboard: every emoji there is, with the keyboard's own search
/// (in the phone's language), skin tones and recently used. The first emoji typed is handed over; the field itself
/// stays empty.
struct EmojiKeyboardField: UIViewRepresentable {
    var placeholder: String
    var onEmoji: (String) -> Void

    func makeUIView(context: Context) -> EmojiTextField {
        let field = EmojiTextField()
        field.placeholder = placeholder
        field.delegate = context.coordinator
        field.font = .preferredFont(forTextStyle: .body)
        field.adjustsFontForContentSizeCategory = true
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.returnKeyType = .done
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return field
    }

    func updateUIView(_ field: EmojiTextField, context: Context) {
        field.placeholder = placeholder
        context.coordinator.onEmoji = onEmoji
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onEmoji: onEmoji)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var onEmoji: (String) -> Void

        init(onEmoji: @escaping (String) -> Void) {
            self.onEmoji = onEmoji
        }

        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange,
                       replacementString string: String) -> Bool {
            if let emoji = string.first(where: \.isEmoji) {
                textField.resignFirstResponder()
                onEmoji(String(emoji))
            }
            return false
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }
    }
}

/// Asks iOS for the emoji keyboard. If the phone has no emoji keyboard among its keyboards, it gets the usual one.
final class EmojiTextField: UITextField {
    override var textInputContextIdentifier: String? { "" }

    override var textInputMode: UITextInputMode? {
        UITextInputMode.activeInputModes.first { $0.primaryLanguage == "emoji" } ?? super.textInputMode
    }
}

extension Character {
    /// An emoji as people see one: drawn as an emoji by default, or made one (❤️, 1️⃣, flags, joined sequences).
    var isEmoji: Bool {
        guard let first = unicodeScalars.first else { return false }
        return first.properties.isEmojiPresentation || (first.properties.isEmoji && unicodeScalars.count > 1)
    }
}
