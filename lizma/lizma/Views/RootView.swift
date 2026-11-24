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
            // Asks for notifications once; afterwards keeps this phone's token with the signed-in user.
            if auth.isSignedIn {
                await PushManager.shared.start()
                // The home screen widget gets its key and shows friends' songs.
                await WidgetBridge.signedIn()
            } else {
                WidgetBridge.signedOut()
            }
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
        // A song tapped in the home screen widget: MainTabs opens the post (once signed in).
        if let postId = DeepLink.parsePostId(from: url) {
            PushManager.shared.pendingRoute = .post(id: postId, showComments: false)
            return
        }
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
            inviteAlert = InviteAlert(title: String(localized: "Friend invite", bundle: .app), message: message)
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
                inviteAlert = InviteAlert(title: String(localized: "Couldn't accept invite", bundle: .app),
                                          message: String(localized: "\(error.userMessage) We'll try again the next time you open Lizma.", bundle: .app))
                return
            }
            // Used, expired, unknown or your own link: the server's message says which.
            inviteAlert = InviteAlert(title: String(localized: "Couldn't accept invite", bundle: .app), message: error.userMessage)
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
