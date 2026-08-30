import SwiftUI

struct RootView: View {
    @StateObject private var auth = AuthViewModel()

    var body: some View {
        Group {
            if auth.isSignedIn {
                MainTabs()
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            } else {
                AuthScreen()
                    .transition(.opacity)
            }
        }
        .environmentObject(auth)
        .animation(.easeInOut, value: auth.isSignedIn)
        .onOpenURL { url in
            Task { await handle(url: url) }
        }
        .task {
            await acceptPendingIfAny()
        }
    }

    private func handle(url: URL) async {
        if let username = DeepLink.parseInviteUsername(from: url) {
            if auth.isSignedIn {
                try? await FriendService.shared.acceptInvite(byUsername: username)
            } else {
                DeepLink.storePending(username)
            }
        }
    }

    private func acceptPendingIfAny() async {
        guard auth.isSignedIn, let username = DeepLink.takePending() else { return }
        try? await FriendService.shared.acceptInvite(byUsername: username)
    }
}

#Preview { RootView() }
