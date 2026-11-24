import SwiftUI

/// A profile opened over another screen (from the feed or a notification). Pulling down refreshes the
/// profile rather than closing the sheet, so it has its own close button.
struct ProfileSheet: View {
    let username: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            UserProfileScreen(username: username)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                        }
                        .accessibilityLabel("Close")
                    }
                }
        }
    }
}
