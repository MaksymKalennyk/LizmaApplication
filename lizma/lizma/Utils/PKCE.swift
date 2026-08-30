import Foundation
import CryptoKit

struct PKCE {
    let verifier: String
    let challenge: String

    init() {
        self.verifier = PKCE.randomURLSafe(length: 64)
        self.challenge = PKCE.sha256Base64URL(verifier)
    }

    private static func randomURLSafe(length: Int) -> String {
        let chars = Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")
        var s = ""
        for _ in 0..<length { s.append(chars.randomElement()!) }
        return s
    }

    private static func sha256Base64URL(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        let b64 = Data(hash).base64EncodedString()
        return b64.replacingOccurrences(of: "+", with: "-")
                  .replacingOccurrences(of: "/", with: "_")
                  .replacingOccurrences(of: "=", with: "")
    }
}
