import Foundation
import Security

enum KeychainError: Error {
    case unexpectedStatus(OSStatus)
    case dataEncodingFailed
    case noData
}

/// Small strings in the Keychain, readable only on this device after its first unlock (never in backups). Without an
/// access group an item is the app's own; with the App Group's id the app's extensions can read it too.
struct Keychain {
    static func set(_ value: String, forKey key: String, accessGroup: String? = nil) throws {
        guard let data = value.data(using: .utf8) else { throw KeychainError.dataEncodingFailed }
        // Remove the old item with a plain lookup query (a query containing the data would not match it).
        delete(key, accessGroup: accessGroup)
        var attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        if let accessGroup {
            attributes[kSecAttrAccessGroup as String] = accessGroup
        }
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
    }

    static func get(_ key: String, accessGroup: String? = nil) throws -> String {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        guard let data = item as? Data, let string = String(data: data, encoding: .utf8) else {
            throw KeychainError.noData
        }
        return string
    }

    static func delete(_ key: String, accessGroup: String? = nil) {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        SecItemDelete(query as CFDictionary)
    }
}
