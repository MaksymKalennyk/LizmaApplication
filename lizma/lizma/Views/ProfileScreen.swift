import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @StateObject private var requestsVM = RequestsViewModel()
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

                requestSections

                if let err = requestsVM.error {
                    Section {
                        Text(err).foregroundStyle(.red)
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
                await requestsVM.reload()
                await MusicStatusStore.shared.refresh()
                await legal.loadIfNeeded()
            }
        }
        .task {
            username = AuthService.shared.currentUsername
            await requestsVM.reload()
            await legal.loadIfNeeded()
        }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            Task { await requestsVM.reload() }
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

    // MARK: - Friend requests

    @ViewBuilder
    private var requestSections: some View {
        if requestsVM.loading && !requestsVM.hasLoaded {
            Section("Friend requests") {
                ProgressView()
            }
        } else if requestsVM.received.isEmpty && requestsVM.sent.isEmpty {
            Section("Friend requests") {
                Text("No pending requests").foregroundStyle(.secondary)
            }
        } else {
            if !requestsVM.received.isEmpty {
                Section("Friend requests") {
                    ForEach(requestsVM.received) { req in
                        IncomingRequestRow(req: req,
                                           isBusy: requestsVM.inFlight.contains(req.id),
                                           accept: { Task { await requestsVM.accept(req) } },
                                           decline: { Task { await requestsVM.reject(req) } })
                    }
                }
            }
            if !requestsVM.sent.isEmpty {
                Section("Sent requests") {
                    ForEach(requestsVM.sent) { req in
                        HStack(spacing: 12) {
                            AvatarView(username: req.recipientUsername)
                            Text(req.recipientUsername)
                                .lineLimit(1)
                                .truncationMode(.middle)
                            Spacer(minLength: 8)
                            Text("Pending")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
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

private struct IncomingRequestRow: View {
    let req: FriendRequestDto
    let isBusy: Bool
    let accept: () -> Void
    let decline: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(username: req.requesterUsername)

            Text(req.requesterUsername)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: 8)

            if isBusy {
                ProgressView()
            } else {
                HStack(spacing: 8) {
                    Button("Accept", action: accept).buttonStyle(.borderedProminent)
                    Button("Decline", action: decline).buttonStyle(.bordered)
                }
                .controlSize(.small)
                .lineLimit(1)
                .fixedSize()
            }
        }
    }
}
