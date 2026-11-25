import Foundation

enum APIError: Error, LocalizedError {
    case invalidURL
    case decodingFailed
    case server(code: Int, message: String)
    case unauthorized
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL: return String(localized: "Something went wrong. Please try again.", bundle: .app)
        case .decodingFailed: return String(localized: "The server sent an unexpected answer. Please try again.", bundle: .app)
        case .server(_, let message): return message
        case .unauthorized: return String(localized: "Session expired, please sign in again", bundle: .app)
        case .unknown: return String(localized: "Something went wrong. Please try again.", bundle: .app)
        }
    }
}

extension Notification.Name {
    /// Posted when the server no longer accepts the stored token (expired, invalid, or the user is gone).
    static let sessionExpired = Notification.Name("lizma.sessionExpired")
}

final class APIClient {
    static let shared = APIClient()
    private init() {}

    /// What a request carries: JSON, or a file as multipart/form-data.
    enum Payload {
        case json(Encodable)
        case file(field: String, fileName: String, mimeType: String, data: Data)
    }

    private func makeRequest(path: String,
                             method: String,
                             query: [URLQueryItem],
                             payload: Payload?,
                             token: String?) throws -> URLRequest {
        let normalizedPath = path.hasPrefix("/") ? path : "/" + path
        let fullURLString = AppConfig.apiBaseURL + normalizedPath

        guard var components = URLComponents(string: fullURLString) else {
            throw APIError.invalidURL
        }

        if !query.isEmpty {
            components.queryItems = (components.queryItems ?? []) + query
            // URLComponents escapes "&" and "=" inside values but leaves "+", which servers decode as a space.
            components.percentEncodedQuery = components.percentEncodedQuery?
                .replacingOccurrences(of: "+", with: "%2B")
        }

        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // The server answers in the app's language (its messages, the legal pages' links): the one chosen in
        // Profile → Language, read for every request so a change applies at once.
        request.setValue(LanguageSettings.shared.code, forHTTPHeaderField: "Accept-Language")

        // Sign-in/sign-up must not carry a (possibly stale) token from a previous session.
        if !normalizedPath.hasPrefix("/auth/"), let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        switch payload {
        case .json(let body):
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(AnyEncodable(body))
        case .file(let field, let fileName, let mimeType, let data):
            let boundary = "lizma-\(UUID().uuidString)"
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            var body = Data()
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(field)\"; filename=\"\(fileName)\"\r\n".utf8))
            body.append(Data("Content-Type: \(mimeType)\r\n\r\n".utf8))
            body.append(data)
            body.append(Data("\r\n--\(boundary)--\r\n".utf8))
            request.httpBody = body
        case nil:
            break
        }
        return request
    }

    func send<T: Decodable>(_ type: T.Type,
                            path: String,
                            method: String = "GET",
                            query: [URLQueryItem] = [],
                            body: Encodable? = nil) async throws -> T {
        try await send(type, path: path, method: method, query: query, payload: body.map { Payload.json($0) }, isRetry: false)
    }

    /// The bytes behind an address only the signed-in user may open (a screenshot for moderators). The session
    /// is renewed once if the token ran out, like for any other request.
    func download(path: String) async throws -> Data {
        try await download(path: path, isRetry: false)
    }

    private func download(path: String, isRetry: Bool) async throws -> Data {
        let token = await AuthService.shared.validAccessToken()
        var request = try makeRequest(path: path, method: "GET", query: [], payload: nil, token: token)
        request.setValue("image/*", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }
        switch http.statusCode {
        case 200..<300:
            return data
        case 401 where !isRetry:
            if case .renewed = await SessionRenewal.shared.renew() {
                return try await download(path: path, isRetry: true)
            }
            throw APIError.unauthorized
        default:
            throw APIError.server(code: http.statusCode, message: prettyMessage(code: http.statusCode, fallback: nil))
        }
    }

    /// Uploads a file (multipart/form-data) and decodes the answer.
    func upload<T: Decodable>(_ type: T.Type, path: String, method: String = "PUT",
                              field: String, fileName: String, mimeType: String, data: Data) async throws -> T {
        try await send(type, path: path, method: method, query: [],
                       payload: .file(field: field, fileName: fileName, mimeType: mimeType, data: data), isRetry: false)
    }

    private func send<T: Decodable>(_ type: T.Type,
                                    path: String,
                                    method: String,
                                    query: [URLQueryItem],
                                    payload: Payload?,
                                    isRetry: Bool) async throws -> T {
        // Sign-in, sign-up and refresh go without a token; everything else with one that is still good.
        let isAuthPath = (path.hasPrefix("/") ? path : "/" + path).hasPrefix("/auth/")
        let token = isAuthPath ? nil : await AuthService.shared.validAccessToken()
        let request = try makeRequest(path: path, method: method, query: query, payload: payload, token: token)
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
            // Callers that don't care about the body: any 2xx (including an empty body) is a success.
            if T.self == Empty.self {
                return Empty() as! T
            }
            if T.self == EmptyResponse.self {
                return EmptyResponse() as! T
            }
            do {
                return try JSONDecoder().decode(T.self, from: data)
            } catch {
                throw APIError.decodingFailed
            }

        case 401 where request.value(forHTTPHeaderField: "Authorization") != nil:
            // The token we sent was rejected. Usually it just ran out: renew the session once and try again.
            if !isRetry {
                switch await SessionRenewal.shared.renew() {
                case .renewed:
                    return try await send(type, path: path, method: method, query: query, payload: payload, isRetry: true)
                case .failed:
                    // The session may still be fine; don't sign out over a network hiccup.
                    throw APIError.server(code: 401, message: String(localized: "Couldn't renew your session. Check your connection and try again.", bundle: .app))
                case .ended:
                    break
                }
            }
            // The session is over: forget it and send the user back to the sign-in screen.
            AuthService.shared.signOut()
            await MainActor.run { NotificationCenter.default.post(name: .sessionExpired, object: nil) }
            throw APIError.unauthorized

        case 401:
            let msg = extractMessage(from: data) ?? String(localized: "Incorrect username or password", bundle: .app)
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
        case 400: return String(localized: "Please check what you entered.", bundle: .app)
        case 401: return String(localized: "Incorrect username or password", bundle: .app)
        case 404: return String(localized: "Not found", bundle: .app)
        case 409: return String(localized: "This conflicts with something that already exists.", bundle: .app)
        case 500: return String(localized: "Server error. Please try again later.", bundle: .app)
        default:  return String(localized: "Error (\(code))", bundle: .app)
        }
    }
}

extension Error {
    /// True when a task or its URLSession request was cancelled (typing, leaving a screen): not worth showing.
    var isCancellation: Bool {
        if self is CancellationError { return true }
        if let urlError = self as? URLError, urlError.code == .cancelled { return true }
        return false
    }

    /// A message suitable for showing to the user.
    var userMessage: String {
        (self as? LocalizedError)?.errorDescription ?? localizedDescription
    }
}
