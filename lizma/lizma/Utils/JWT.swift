import Foundation

/// Reads the (unverified) payload of the backend's JWT. The server stays the authority; this is only
/// used to know who we are and when the session ends.
enum JWT {
    static func payload(of token: String) -> [String: Any]? {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }

        var base64 = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }

        guard let data = Data(base64Encoded: base64),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return object
    }

    /// The username the token was issued for (`sub`).
    static func username(from token: String) -> String? {
        guard let payload = payload(of: token) else { return nil }
        return (payload["username"] as? String) ?? (payload["sub"] as? String)
    }

    /// When the token expires (`exp`), if it says so.
    static func expirationDate(of token: String) -> Date? {
        guard let exp = payload(of: token)?["exp"] as? NSNumber else { return nil }
        return Date(timeIntervalSince1970: exp.doubleValue)
    }
}
