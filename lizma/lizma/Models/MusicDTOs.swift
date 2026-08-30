import Foundation

struct AppleDevTokenDTO: Decodable {
    let developerToken: String
}

struct SpotifyExchangeRequest: Encodable {
    let code: String
    let redirectUri: String
    let codeVerifier: String
}

struct EmptyResponse: Decodable {}

struct ProvidersStatus: Decodable {
    var spotifyConnected: Bool
    var appleConnected: Bool
}
