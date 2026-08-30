import SwiftUI

struct MainTabs: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            FeedScreen()
                .tabItem { Label("Feed", systemImage: "music.note.house") }
                .tag(0)
                
            FriendsScreen()
                .tabItem { Label("Friends", systemImage: "person.2.fill") }
                .tag(1)
                
            PostComposerView(selectedTab: $selectedTab)
                .tabItem { Label("Post", systemImage: "plus.square.fill") }
                .tag(2)
                
            ProfileScreen()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                .tag(3)
        }
    }
}
