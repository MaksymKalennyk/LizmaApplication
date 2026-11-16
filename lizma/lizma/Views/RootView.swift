import SwiftUI

struct RootView: View {
    @StateObject private var auth = AuthViewModel()
    @State private var inviteAlert: InviteAlert?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Group {
            if auth.isSignedIn {
                MainTabs()
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            } else {
                AuthScreen()
                    // Passed on explicitly: while this screen fades out after signing in, the feed
                    // already makes the window dark, and without this the screen would flash dark.
                    .environment(\.colorScheme, colorScheme)
                    .transition(.opacity)
            }
        }
        .environmentObject(auth)
        .animation(.easeInOut, value: auth.isSignedIn)
        .onOpenURL { url in
            handle(url: url)
        }
        // Runs at launch and again on every sign-in, so an invite opened while signed out
        // is accepted right after signing in or up.
        .task(id: auth.isSignedIn) {
            await acceptPendingIfAny()
        }
        .alert(inviteAlert?.title ?? "", isPresented: isShowingInviteAlert, presenting: inviteAlert) { _ in
            Button("OK", role: .cancel) {}
        } message: { alert in
            Text(alert.message)
        }
    }

    private var isShowingInviteAlert: Binding<Bool> {
        Binding(get: { inviteAlert != nil }, set: { if !$0 { inviteAlert = nil } })
    }

    private func handle(url: URL) {
        guard let token = DeepLink.parseInviteToken(from: url) else { return }
        if auth.isSignedIn {
            Task { await acceptInvite(token: token) }
        } else {
            DeepLink.storePendingInvite(token)
        }
    }

    private func acceptPendingIfAny() async {
        guard auth.isSignedIn, let token = DeepLink.takePendingInvite() else { return }
        await acceptInvite(token: token)
    }

    private func acceptInvite(token: String) async {
        do {
            // "Friendship established" or "You are already friends".
            let message = try await FriendService.shared.acceptInvite(token: token)
            inviteAlert = InviteAlert(title: "Friend invite", message: message)
            NotificationCenter.default.post(name: .friendsChanged, object: nil)
        } catch {
            if error.isCancellation || isSessionExpiry(error) {
                // Not the invite's fault: keep it and accept it after the next sign-in.
                DeepLink.storePendingInvite(token)
                return
            }
            if error is URLError {
                // Offline or server unreachable: the invite is still valid, retry on the next launch.
                DeepLink.storePendingInvite(token)
                inviteAlert = InviteAlert(title: "Couldn't accept invite",
                                          message: "\(error.userMessage) We'll try again the next time you open Lizma.")
                return
            }
            // Used, expired, unknown or your own link: the server's message says which.
            inviteAlert = InviteAlert(title: "Couldn't accept invite", message: error.userMessage)
        }
    }

    private func isSessionExpiry(_ error: Error) -> Bool {
        if case APIError.unauthorized = error { return true }
        return false
    }
}

private struct InviteAlert {
    let title: String
    let message: String
}

#Preview { RootView() }
