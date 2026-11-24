import SwiftUI

/// Find any account, suspended ones included, and suspend, reinstate or delete it.
struct ModerationUsersScreen: View {
    @State private var query = ""
    @State private var users: [AdminUser] = []
    @State private var hasLoaded = false
    @State private var errorMessage: String?
    @State private var selected: AdminUser?
    @State private var isConfirmingDelete = false
    @State private var isWorking = false

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        List {
            if let errorMessage {
                Section {
                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }

            if !hasLoaded {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
            } else if users.isEmpty {
                Group {
                    if trimmedQuery.isEmpty {
                        ContentUnavailableView("No suspended users", systemImage: "person.2",
                                               description: Text("Search by username to find anyone."))
                    } else {
                        ContentUnavailableView.search(text: trimmedQuery)
                    }
                }
                .listRowBackground(Color.clear)
            } else {
                Section(trimmedQuery.isEmpty ? "Suspended" : "Users") {
                    ForEach(users) { user in
                        Button {
                            selected = user
                        } label: {
                            row(user)
                        }
                        .disabled(user.admin)
                        // On the row, so the choices come out of the user they are about.
                        .confirmationDialog(user.username,
                                            isPresented: Binding(get: { selected?.id == user.id && !isConfirmingDelete },
                                                                 set: { if !$0, !isConfirmingDelete, selected?.id == user.id { selected = nil } }),
                                            titleVisibility: .visible) {
                            if user.banned {
                                Button("Lift the suspension") { Task { await act { try await ModerationService.shared.unban(username: user.username) } } }
                            } else {
                                Button("Suspend", role: .destructive) { Task { await act { try await ModerationService.shared.ban(username: user.username) } } }
                            }
                            Button("Delete account…", role: .destructive) { isConfirmingDelete = true }
                            Button("Cancel", role: .cancel) { selected = nil }
                        } message: {
                            Text(user.banned
                                 ? "Suspended: can't sign in. Lifting it doesn't bring back what was removed."
                                 : "Suspending removes everything they posted and their friendships.")
                        }
                    }
                }
            }
        }
        .navigationTitle("Users")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search by username")
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .task(id: trimmedQuery) {
            // A short pause, so typing doesn't send a request per letter.
            if !trimmedQuery.isEmpty {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
            }
            await load()
        }
        .refreshable { await load() }
        .alert("Delete \(selected?.username ?? String(localized: "this account", bundle: .app))?", isPresented: $isConfirmingDelete, presenting: selected) { user in
            Button("Delete", role: .destructive) {
                Task { await act { try await ModerationService.shared.deleteAccount(username: user.username) } }
            }
            Button("Cancel", role: .cancel) { selected = nil }
        } message: { _ in
            Text("The account and all of its data are deleted for good, and the username becomes free. Use it for deletion requests sent to support.")
        }
        .disabled(isWorking)
        .overlay {
            if isWorking { ProgressView() }
        }
    }

    private func row(_ user: AdminUser) -> some View {
        HStack(spacing: 12) {
            UserLabel(username: user.username, displayName: user.displayName, avatarUrl: user.avatarUrl)
                .opacity(user.banned ? 0.5 : 1)
            Spacer(minLength: 8)
            if user.admin {
                tag(String(localized: "Moderator", bundle: .app), color: .accentColor)
            } else if user.banned {
                tag(String(localized: "Suspended", bundle: .app), color: .red)
            }
        }
    }

    private func tag(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.12), in: Capsule())
    }

    private func load() async {
        let current = trimmedQuery
        do {
            let found = try await ModerationService.shared.users(matching: current)
            guard current == trimmedQuery else { return }
            users = found
            errorMessage = nil
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
        hasLoaded = true
    }

    private func act(_ action: () async throws -> Void) async {
        isWorking = true
        defer {
            isWorking = false
            selected = nil
            isConfirmingDelete = false
        }
        do {
            try await action()
            errorMessage = nil
            await ModerationStore.shared.refresh()
            await load()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}
