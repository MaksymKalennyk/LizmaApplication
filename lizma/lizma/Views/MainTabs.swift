import SwiftUI

struct MainTabs: View {
    /// The feed, or Profile right after the language was changed there (the change rebuilds the app).
    @State private var selectedTab = LanguageStore.shared.takeStartTab()
    /// Friend requests live here, above the tabs, so the Friends tab can show how many are waiting.
    @StateObject private var requests = RequestsViewModel()
    @Environment(\.scenePhase) private var scenePhase
    /// Moderators see on the Profile tab how many reports and feedback wait for them.
    @ObservedObject private var moderation = ModerationStore.shared
    /// A tapped notification says where to go.
    @ObservedObject private var push = PushManager.shared
    @State private var routedPost: RoutedPost?
    @State private var routedProfile: String?

    var body: some View {
        TabView(selection: tabSelection) {
            FeedScreen()
                .tabItem { Label("Feed", systemImage: "music.note.house") }
                .tag(0)

            FriendsScreen()
                .tabItem { Label("Friends", systemImage: "person.2.fill") }
                .badge(requests.received.count)
                .tag(1)

            PostComposerView(selectedTab: $selectedTab)
                .tabItem { Label("Post", systemImage: "plus.square.fill") }
                .tag(2)

            ProfileScreen()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                .badge(moderation.summary?.total ?? 0)
                .tag(3)
        }
        .environmentObject(requests)
        // The feed is a dark screen in both appearances, and the tab bar, status bar and alerts over it
        // follow. Set here, by the selected tab: set inside the feed it would stay on for every tab.
        .preferredColorScheme(selectedTab == 0 ? .dark : nil)
        .task {
            await requests.reload()
            await moderation.refresh()
        }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            Task { await requests.reload() }
        }
        .onChange(of: scenePhase) { _, phase in
            // Requests may have arrived while the app was in the background.
            if phase == .active {
                Task {
                    await requests.reload()
                    await moderation.refresh()
                }
            }
        }
        .onChange(of: selectedTab) {
            // Not everyone has notifications on: moving between tabs keeps the counters fresh.
            Task {
                await requests.reload()
                await moderation.refresh()
            }
        }
        // A notification tapped now, or the one that opened the app.
        .onChange(of: push.pendingRoute) { _, route in
            follow(route)
        }
        .task {
            follow(push.pendingRoute)
        }
        .fullScreenCover(item: $routedPost) { routed in
            RoutedPostPager(posts: [routed.post], opensComments: routed.opensComments)
        }
        .sheet(item: Binding(get: { routedProfile.map { FeedScreen.IdentifiableString(value: $0) } },
                             set: { routedProfile = $0?.value })) { wrapper in
            ProfileSheet(username: wrapper.value)
        }
    }

    private func follow(_ route: PushRoute?) {
        guard let route else { return }
        push.pendingRoute = nil
        switch route {
        case .friendRequests:
            selectedTab = 1
            Task { await requests.reload() }
        case .profile(let username):
            routedProfile = username
        case .post(let id, let showComments):
            Task {
                // Deleted, or no longer a friend's: there's nothing to open.
                if let post = try? await PostService.shared.getPost(postId: id) {
                    routedPost = RoutedPost(post: post, opensComments: showComments)
                }
            }
        case .composer:
            selectedTab = 2
        }
    }

    /// The tab bar also reports a tap on the tab that is already selected, the usual way back to the top.
    private var tabSelection: Binding<Int> {
        Binding(get: { selectedTab }, set: { tapped in
            if tapped == selectedTab, tapped == 0 {
                NotificationCenter.default.post(name: .feedTabReselected, object: nil)
            }
            selectedTab = tapped
        })
    }
}

/// The post a notification is about.
private struct RoutedPost: Identifiable {
    let post: PostResponse
    let opensComments: Bool
    var id: Int { post.id }
}

/// That post on its own full-screen page, as when opened from a profile.
private struct RoutedPostPager: View {
    @State var posts: [PostResponse]
    let opensComments: Bool

    var body: some View {
        PostPagerView(posts: $posts, startPostId: posts.first?.id ?? 0, opensComments: opensComments)
    }
}
