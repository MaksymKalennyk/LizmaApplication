import SwiftUI

struct MainTabs: View {
    @State private var selectedTab = 0
    /// Friend requests live here, above the tabs, so the Friends tab can show how many are waiting.
    @StateObject private var requests = RequestsViewModel()
    @Environment(\.scenePhase) private var scenePhase

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
                .tag(3)
        }
        .environmentObject(requests)
        // The feed is a dark screen in both appearances, and the tab bar, status bar and alerts over it
        // follow. Set here, by the selected tab: set inside the feed it would stay on for every tab.
        .preferredColorScheme(selectedTab == 0 ? .dark : nil)
        .task {
            await requests.reload()
        }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            Task { await requests.reload() }
        }
        .onChange(of: scenePhase) { _, phase in
            // Requests may have arrived while the app was in the background.
            if phase == .active {
                Task { await requests.reload() }
            }
        }
        .onChange(of: selectedTab) {
            // There are no push notifications yet: moving between tabs keeps the counter fresh.
            Task { await requests.reload() }
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
