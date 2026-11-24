import SwiftUI

/// Profile → Language: the phone's language, English or Ukrainian.
struct LanguageSettingsView: View {
    @ObservedObject private var language = LanguageStore.shared

    var body: some View {
        Form {
            Section {
                option(.system, title: Text("Phone's language"),
                       subtitle: Text(verbatim: AppLanguage.endonym(of: LanguageSettings.shared.systemCode)))
                option(.english, title: Text(verbatim: "English"), subtitle: nil)
                option(.ukrainian, title: Text(verbatim: "Українська"), subtitle: nil)
            } footer: {
                Text("The phone's own screens, such as the photo picker, follow the next time you open Lizma.")
            }
        }
        .navigationTitle("Language")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func option(_ value: AppLanguage, title: Text, subtitle: Text?) -> some View {
        Button {
            language.choose(value)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    // Colors, not the hierarchical styles: inside a button those would take the tint.
                    title
                        .foregroundStyle(Color.primary)
                    if let subtitle {
                        subtitle
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                }
                Spacer()
                if language.choice == value {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.tint)
                }
            }
            .contentShape(Rectangle())
        }
        .accessibilityAddTraits(language.choice == value ? .isSelected : [])
    }
}

#Preview {
    NavigationStack { LanguageSettingsView() }
}
