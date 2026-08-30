import Foundation

enum APIError: Error, LocalizedError {
    case invalidURL
    case decodingFailed
    case server(code: Int, message: String)
    case unauthorized
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .decodingFailed: return "Failed to decode server response"
        case .server(_, let message): return message
        case .unauthorized: return "Unauthorized. Please sign in again."
        case .unknown: return "Unknown error"
        }
    }
}

final class APIClient {
    static let shared = APIClient()
    private init() {}

    private var token: String? { (try? Keychain.get("jwtToken")) }

    private func makeRequest(path: String,
                             method: String = "GET",
                             query: [URLQueryItem] = [],
                             body: Encodable? = nil) throws -> URLRequest {
        let normalizedPath = path.hasPrefix("/") ? path : "/" + path
        let fullURLString = AppConfig.apiBaseURL + normalizedPath
        
        guard var components = URLComponents(string: fullURLString) else {
            throw APIError.invalidURL
        }

        if !query.isEmpty {
            var currentQueryItems = components.queryItems ?? []
            currentQueryItems.append(contentsOf: query)
            components.queryItems = currentQueryItems.isEmpty ? nil : currentQueryItems
        }
        
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body = body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(AnyEncodable(body))
        }
        return request
    }

    func send<T: Decodable>(_ type: T.Type,
                            path: String,
                            method: String = "GET",
                            query: [URLQueryItem] = [],
                            body: Encodable? = nil) async throws -> T {
        let request = try makeRequest(path: path, method: method, query: query, body: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        switch http.statusCode {
        case 200..<300:
            
            if T.self == String.self {
                if data.isEmpty { return "" as! T }
                if let jsonString = try? JSONDecoder().decode(String.self, from: data) {
                    return jsonString as! T
                }
                if let text = String(data: data, encoding: .utf8) {
                    return text as! T
                }
                throw APIError.decodingFailed
            }
            if T.self == Empty.self {
                return Empty() as! T
            }
            do {
                return try JSONDecoder().decode(T.self, from: data)
            } catch {
                throw APIError.decodingFailed
            }

        case 401:
            let msg = extractMessage(from: data) ?? "Incorrect username or password"
            throw APIError.server(code: http.statusCode, message: msg)

        default:
            let serverMsg = extractMessage(from: data)
            let nice = prettyMessage(code: http.statusCode, fallback: serverMsg)
            throw APIError.server(code: http.statusCode, message: nice)
        }
    }

    private func extractMessage(from data: Data?) -> String? {
        guard let data, !data.isEmpty else { return nil }
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let msg = obj["message"] as? String, !msg.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return msg
            }
            if let err = obj["error"] as? String, !err.isEmpty { return err }
            if let detail = obj["detail"] as? String, !detail.isEmpty { return detail }
            if let errors = obj["errors"] as? [[String: Any]] {
                let parts = errors.compactMap { $0["defaultMessage"] as? String ?? $0["message"] as? String }
                if !parts.isEmpty { return parts.joined(separator: "\n") }
            }
        }
        let text = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
        return text?.isEmpty == false ? text : nil
    }

    private func prettyMessage(code: Int, fallback: String?) -> String {
        if let fallback, !fallback.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return fallback
        }
        switch code {
        case 400: return "Validation error"
        case 401: return "Incorrect username or password"
        case 404: return "User not found"
        case 409: return "Username already taken"
        case 500: return "Server error"
        default:  return "Error (\(code))"
        }
    }
}
