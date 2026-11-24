import Foundation
@testable import lizma

/// Answers the app's requests instead of the backend. Registered for URLSession.shared, which the app uses.
final class MockServer: URLProtocol {
    struct Response {
        var status: Int = 200
        var json: String = "{}"
    }

    struct Received {
        let method: String
        let path: String
        let headers: [String: String]
        let body: String
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var handler: ((Received) throws -> Response)?
    nonisolated(unsafe) private static var log: [Received] = []

    /// Starts answering with `handler`; `URLError`s it throws reach the app as network failures.
    static func start(_ handler: @escaping (Received) throws -> Response) {
        lock.withLock {
            Self.handler = handler
            log = []
        }
        URLProtocol.registerClass(MockServer.self)
    }

    static func stop() {
        URLProtocol.unregisterClass(MockServer.self)
        lock.withLock { handler = nil }
    }

    static var received: [Received] {
        lock.withLock { log }
    }

    static func received(_ method: String, _ path: String) -> [Received] {
        received.filter { $0.method == method && $0.path == path }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        let received = Received(method: request.httpMethod ?? "GET",
                                path: request.url?.path ?? "",
                                headers: request.allHTTPHeaderFields ?? [:],
                                body: Self.body(of: request))
        let handler = Self.lock.withLock { () -> ((Received) throws -> Response)? in
            Self.log.append(received)
            return Self.handler
        }
        do {
            guard let handler else { throw URLError(.notConnectedToInternet) }
            let response = try handler(received)
            let http = HTTPURLResponse(url: request.url!, statusCode: response.status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(response.json.utf8))
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    /// URLSession hands bodies over as a stream.
    private static func body(of request: URLRequest) -> String {
        if let data = request.httpBody { return String(decoding: data, as: UTF8.self) }
        guard let stream = request.httpBodyStream else { return "" }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return String(decoding: data, as: UTF8.self)
    }
}

/// An (unsigned) access token like the backend's: the app only reads its payload.
func makeAccessToken(username: String = "tester", expiresIn seconds: TimeInterval) -> String {
    func base64URL(_ json: String) -> String {
        Data(json.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    let expiry = Int(Date().addingTimeInterval(seconds).timeIntervalSince1970)
    return base64URL(#"{"alg":"HS256"}"#) + "." + base64URL(#"{"sub":"\#(username)","id":1,"exp":\#(expiry)}"#) + ".signature"
}

func tokensJSON(access: String, refresh: String) -> String {
    #"{"token":"\#(access)","refreshToken":"\#(refresh)","expiresIn":900}"#
}
