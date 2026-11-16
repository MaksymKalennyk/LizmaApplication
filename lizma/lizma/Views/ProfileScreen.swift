import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @ObservedObject private var legal = LegalInfoStore.shared

    @State private var username: String? = nil
    @State private var showDeleteAccount = false
    @State private var isConfirmingSignOut = false
    @State private var safariLink: SafariLink? = nil

    var body: some View {
        NavigationStack {
            Form {
                if let username {
                    Section {
                        HStack(spacing: 14) {
                            AvatarView(username: username, size: 56)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(username)
                                    .font(.title3.weight(.semibold))
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                                Text("Your username")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                        .accessibilityElement(children: .combine)
                    }
                }

                Section {
                    MusicAccountsStatusRow()
                }

                Section("Privacy & safety") {
                    NavigationLink {
                        BlockedUsersView()
                    } label: {
                        Label("Blocked users", systemImage: "hand.raised")
                    }
                }

                Section {
                    Button {
                        open(legal.termsURL)
                    } label: {
                        Label("Terms of Use", systemImage: "doc.text")
                    }
                    Button {
                        open(legal.privacyURL)
                    } label: {
                        Label("Privacy Policy", systemImage: "lock.shield")
                    }
                    if let supportMail = legal.supportMailURL {
                        Link(destination: supportMail) {
                            Label("Contact support", systemImage: "envelope")
                        }
                    }
                } header: {
                    Text("About")
                } footer: {
                    if let version = Self.appVersion {
                        Text("Lizma \(version)")
                    }
                }

                // Leaving and deleting sit at the very bottom, away from everyday actions.
                Section {
                    Button {
                        isConfirmingSignOut = true
                    } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                    .confirmationDialog("Sign out of Lizma?", isPresented: $isConfirmingSignOut, titleVisibility: .visible) {
                        Button("Sign out", role: .destructive) { auth.signOut() }
                        Button("Cancel", role: .cancel) {}
                    }

                    Button(role: .destructive) {
                        showDeleteAccount = true
                    } label: {
                        // Explicit colour: the destructive role alone leaves the icon in the tint colour.
                        Label("Delete account", systemImage: "trash")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Profile")
            .refreshable {
                await MusicStatusStore.shared.refresh()
                await legal.loadIfNeeded()
            }
        }
        .task {
            username = AuthService.shared.currentUsername
            await legal.loadIfNeeded()
        }
        .sheet(isPresented: $showDeleteAccount) {
            DeleteAccountView()
                .environmentObject(auth)
        }
        .sheet(item: $safariLink) { link in
            SafariView(url: link.url)
                .ignoresSafeArea()
        }
    }

    // MARK: - Helpers

    private static var appVersion: String? {
        guard let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String else { return nil }
        if let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String {
            return "\(version) (\(build))"
        }
        return version
    }

    private func open(_ url: URL?) {
        guard let url else { return }
        safariLink = SafariLink(url: url)
    }
}
