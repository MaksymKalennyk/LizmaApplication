import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @ObservedObject private var legal = LegalInfoStore.shared
    @ObservedObject private var moderation = ModerationStore.shared
    @ObservedObject private var currentUser = CurrentUserStore.shared
    @ObservedObject private var language = LanguageStore.shared

    @State private var username: String? = nil
    @State private var showDeleteAccount = false
    @State private var isConfirmingSignOut = false
    @State private var safariLink: SafariLink? = nil
    @State private var showFeedbackForm = false

    var body: some View {
        NavigationStack {
            Form {
                if let username {
                    // Opens your own profile page: photo, name and your posts.
                    Section {
                        NavigationLink {
                            UserProfileScreen(username: username)
                        } label: {
                            HStack(spacing: 14) {
                                AvatarView(username: username, imageURL: currentUser.me?.avatarUrl, size: 56)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(currentUser.me?.displayName ?? username)
                                        .font(.title3.weight(.semibold))
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                    Text("Profile and my posts")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                            .accessibilityElement(children: .combine)
                        }
                    }
                }

                // Only moderators (the server's admin list) see this.
                if moderation.isModerator {
                    Section {
                        NavigationLink {
                            ModerationScreen()
                        } label: {
                            HStack {
                                Label("Moderation", systemImage: "shield.lefthalf.filled")
                                Spacer(minLength: 8)
                                CountBadge(count: moderation.summary?.total ?? 0)
                            }
                        }
                    }
                }

                Section {
                    NavigationLink {
                        NotificationSettingsView()
                    } label: {
                        Label("Notifications", systemImage: "bell")
                    }
                    NavigationLink {
                        LanguageSettingsView()
                    } label: {
                        LabeledContent {
                            Text(verbatim: AppLanguage.endonym(of: language.code))
                        } label: {
                            Label("Language", systemImage: "globe")
                        }
                    }
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
                    Button {
                        showFeedbackForm = true
                    } label: {
                        Label("Send feedback", systemImage: "lightbulb")
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
                await currentUser.refresh()
                await legal.loadIfNeeded()
                await moderation.refresh()
            }
        }
        .task {
            username = AuthService.shared.currentUsername
            await legal.loadIfNeeded()
        }
        .task {
            await moderation.refresh()
            await currentUser.refresh()
        }
        .sheet(isPresented: $showFeedbackForm) {
            FeedbackFormView()
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
