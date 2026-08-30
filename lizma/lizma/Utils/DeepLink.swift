import Foundation

enum DeepLink {
    static let pendingKey = "pendingInviteUsername"

    static func parseInviteUsername(from url: URL) -> String? {
        let comps = url.pathComponents.filter { $0 != "/" }
        if comps.count >= 2, comps[0] == "u" { return comps[1] }
        if comps.count == 1 { return comps[0] }
        if url.scheme?.hasPrefix("http") == true, comps.count == 1 { return comps[0] }
        return nil
    }

    static func storePending(_ username: String) {
        UserDefaults.standard.set(username, forKey: pendingKey)
    }

    static func takePending() -> String? {
        let t = UserDefaults.standard.string(forKey: pendingKey)
        if t != nil { UserDefaults.standard.removeObject(forKey: pendingKey) }
        return t
    }
}
