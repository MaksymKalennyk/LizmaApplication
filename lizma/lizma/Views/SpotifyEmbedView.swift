import SwiftUI
import WebKit

struct SpotifyEmbedView: View {
    let trackId: String
    
    var body: some View {
        VStack(spacing: 0) {
            
            WebView(url: URL(string: "https://open.spotify.com/embed/track/\(trackId)")!)
                .frame(height: 152) 
                .background(Color.black)
            
            Spacer()
        }
    }
}

