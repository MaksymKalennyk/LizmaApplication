import Foundation
import CryptoKit

actor MusicService {
    static let shared = MusicService()

    private var spotifyCodeVerifier: String?
    private var spotifyState: String?


    func status() async throws -> ProvidersStatus {
        try await get(path: "/music/status")
    }

    func spotifyAuthorizeURL() async throws -> URL {
        let verifier = Self.makeCodeVerifier()
        let challenge = Self.sha256Base64URL(verifier)
        let state = Self.randomURLSafe(length: 16)

        self.spotifyCodeVerifier = verifier
        self.spotifyState = state

        guard let clientId = AppConfig.spotifyClientId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let redirect = AppConfig.spotifyRedirectUri.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)
        else {
            throw NSError(domain: "MusicService", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bad Spotify config"])
        }

        let scopes = [
            "user-read-email",
            "user-read-private",
            "user-read-playback-state",
            "user-modify-playback-state",
            "streaming"
        ].joined(separator: " ")

        guard let scopeEnc = scopes.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw NSError(domain: "MusicService", code: 2, userInfo: [NSLocalizedDescriptionKey: "Bad scopes"])
        }

        let urlString =
          "https://accounts.spotify.com/authorize?" +
          "client_id=\(clientId)" +
          "&response_type=code" +
          "&redirect_uri=\(redirect)" +
          "&scope=\(scopeEnc)" +
          "&code_challenge_method=S256" +
          "&code_challenge=\(challenge)" +
          "&state=\(state)" +
          "&show_dialog=true"

        guard let url = URL(string: urlString) else {
            throw NSError(domain: "MusicService", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bad authorize URL"])
        }
        return url
    }

    func handleSpotifyCallback(url: URL) async throws {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw NSError(domain: "MusicService", code: 10, userInfo: [NSLocalizedDescriptionKey: "Bad callback url"])
        }

        var receivedCode: String?
        var receivedState: String?

        components.queryItems?.forEach { item in
            if item.name == "code" { receivedCode = item.value }
            if item.name == "state" { receivedState = item.value }
            if item.name == "error" {
                
                receivedCode = nil
            }
        }

        guard let code = receivedCode else {
            throw NSError(domain: "MusicService", code: 11, userInfo: [NSLocalizedDescriptionKey: "No authorization code"])
        }

        guard let state = receivedState, state == spotifyState else {
            throw NSError(domain: "MusicService", code: 12, userInfo: [NSLocalizedDescriptionKey: "State mismatch"])
        }

        guard let verifier = spotifyCodeVerifier else {
            throw NSError(domain: "MusicService", code: 13, userInfo: [NSLocalizedDescriptionKey: "Missing code verifier"])
        }

        let body = SpotifyExchangeRequest(code: code, redirectUri: AppConfig.spotifyRedirectUri, codeVerifier: verifier)
        _ = try await post(path: "/music/spotify/exchange", json: body) as EmptyResponse

        spotifyCodeVerifier = nil
        spotifyState = nil
    }

    func appleDeveloperToken() async throws -> String {
        let dto: AppleDevTokenDTO = try await get(path: "/music/apple/dev-token")
        return dto.developerToken
    }

    func connectAppleMusic(userToken: String) async throws {
        struct Payload: Encodable { let userToken: String }
        _ = try await post(path: "/music/apple/connect", json: Payload(userToken: userToken)) as EmptyResponse
    }


    private func get<T: Decodable>(path: String) async throws -> T {
        return try await APIClient.shared.send(T.self, path: path)
    }

    private func post<T: Encodable, R: Decodable>(path: String, json: T) async throws -> R {
        return try await APIClient.shared.send(R.self, path: path, method: "POST", body: json)
    }
    
    func searchSpotify(query: String) async throws -> [SpotifyTrack] {
        guard let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return [] }
        return try await APIClient.shared.send([SpotifyTrack].self, path: "/music/search?query=\(encodedQuery)")
    }


    private static func makeCodeVerifier() -> String {
        
        let bytes = (0..<32).map { _ in UInt8.random(in: 0...255) }
        let data = Data(bytes)
        var s = base64url(data)
        if s.count < 43 {
            s += String(repeating: "A", count: 43 - s.count)
        }
        return s
    }

    private static func sha256Base64URL(_ text: String) -> String {
        let digest = SHA256.hash(data: text.data(using: .utf8)!)
        return base64url(Data(digest))
    }

    private static func base64url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func randomURLSafe(length: Int) -> String {
        let alphabet = Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        return String((0..<length).map { _ in alphabet.randomElement()! })
    }
}
