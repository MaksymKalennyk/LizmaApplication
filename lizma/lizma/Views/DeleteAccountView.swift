import SwiftUI

/// Permanently deletes the user's account after a password check and a final confirmation.
struct DeleteAccountView: View {
    @EnvironmentObject private var auth: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var password = ""
    @State private var isDeleting = false
    @State private var errorMessage: String? = nil
    @State private var isConfirming = false

    private let deletedItems = [
        ("music.note.list", String(localized: "Your posts", bundle: .app)),
        ("bubble.left.and.bubble.right", String(localized: "Your comments and reactions", bundle: .app)),
        ("person.2", String(localized: "Your friends and friend requests", bundle: .app)),
        ("music.quarternote.3", String(localized: "Your connected music accounts", bundle: .app))
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label("This can't be undone", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                        .font(.headline)
                    Text("Deleting your account permanently removes:")
                    ForEach(deletedItems, id: \.1) { icon, text in
                        Label(text, systemImage: icon)
                    }
                }

                Section {
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                        .disabled(isDeleting)
                        .onChange(of: password) { errorMessage = nil }
                } header: {
                    Text("Confirm with your password")
                } footer: {
                    // Under the field, not as a row of its own: a red row reads like another button.
                    if let errorMessage {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }

                Section {
                    Button(role: .destructive) {
                        isConfirming = true
                    } label: {
                        HStack {
                            Text("Delete account")
                            if isDeleting {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(password.isEmpty || isDeleting)
                    // On the button itself: the dialog is a popover on newer iOS and points at its source.
                    .confirmationDialog("Delete your account permanently?",
                                        isPresented: $isConfirming,
                                        titleVisibility: .visible) {
                        Button("Delete account", role: .destructive) {
                            Task { await deleteAccount() }
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text("Your posts, comments, reactions, friends and music connections will be deleted. This can't be undone.")
                    }
                }
            }
            .navigationTitle("Delete account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .interactiveDismissDisabled(isDeleting)
        }
    }

    private func deleteAccount() async {
        guard !isDeleting else { return }
        isDeleting = true
        errorMessage = nil
        defer { isDeleting = false }
        do {
            try await AuthService.shared.deleteAccount(password: password)
            dismiss()
            auth.accountDeleted()
        } catch {
            guard !error.isCancellation else { return }
            // e.g. 400 "Incorrect password"
            errorMessage = error.userMessage
        }
    }
}
