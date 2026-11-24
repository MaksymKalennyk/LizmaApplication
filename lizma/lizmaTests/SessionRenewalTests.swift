import Foundation
import Testing
@testable import lizma

/// The access token lives 15 minutes; the app renews it with the refresh token without the user noticing,
/// and only a session the server ended sends the user back to sign-in.
@Suite(.serialized)
struct SessionRenewalTests {

    private let me = #"{"id":1,"username":"tester","admin":false}"#

    init() {
        AuthService.shared.signOut()
    }

    /// Signs in through the mock with an access token that expires in `seconds`.
    private func signIn(accessExpiresIn seconds: TimeInterval) async throws {
        MockServer.start { request in
            // The previous session's sign-out may still be on its way.
            guard request.path == "/auth/sign-in" else { return .init(json: #"{"message":"ok"}"#) }
            return .init(json: tokensJSON(access: makeAccessToken(expiresIn: seconds), refresh: "refresh-1"))
        }
        try await AuthService.shared.signIn(username: "tester", password: "password123")
    }

    @Test func aTokenAboutToExpireIsRenewedBeforeTheRequest() async throws {
        try await signIn(accessExpiresIn: 30)
        let fresh = makeAccessToken(expiresIn: 900)
        MockServer.start { request in
            switch request.path {
            case "/auth/refresh":
                #expect(request.body.contains("refresh-1"))
                return .init(json: tokensJSON(access: fresh, refresh: "refresh-2"))
            case "/user/me":
                #expect(request.headers["Authorization"] == "Bearer \(fresh)")
                return .init(json: me)
            default:
                return .init(status: 404)
            }
        }
        defer { MockServer.stop() }

        let result = try await ModerationService.shared.me()

        #expect(result.username == "tester")
        #expect(MockServer.received("POST", "/auth/refresh").count == 1)
        #expect(AuthService.shared.token == fresh)
    }

    @Test func requestsAtTheSameTimeShareOneRenewal() async throws {
        try await signIn(accessExpiresIn: 10)
        let fresh = makeAccessToken(expiresIn: 900)
        MockServer.start { request in
            if request.path == "/auth/refresh" {
                // A refresh token works once: a second renewal would end the session.
                Thread.sleep(forTimeInterval: 0.2)
                return .init(json: tokensJSON(access: fresh, refresh: "refresh-2"))
            }
            return .init(json: me)
        }
        defer { MockServer.stop() }

        try await withThrowingTaskGroup(of: Void.self) { group in
            for _ in 0..<5 {
                group.addTask { _ = try await ModerationService.shared.me() }
            }
            try await group.waitForAll()
        }

        #expect(MockServer.received("POST", "/auth/refresh").count == 1)
        #expect(MockServer.received("GET", "/user/me").count == 5)
    }

    @Test func aRejectedTokenIsRenewedAndTheRequestRetried() async throws {
        try await signIn(accessExpiresIn: 900)
        // Different from the one we signed in with (same payload would mean the same token).
        let fresh = makeAccessToken(expiresIn: 1800)
        MockServer.start { request in
            switch request.path {
            case "/auth/refresh":
                return .init(json: tokensJSON(access: fresh, refresh: "refresh-2"))
            case "/user/me" where request.headers["Authorization"] != "Bearer \(fresh)":
                return .init(status: 401, json: #"{"message":"Session expired, please sign in again"}"#)
            default:
                return .init(json: me)
            }
        }
        defer { MockServer.stop() }

        _ = try await ModerationService.shared.me()

        #expect(MockServer.received("POST", "/auth/refresh").count == 1)
        #expect(MockServer.received("GET", "/user/me").count == 2)
        #expect(AuthService.shared.isSignedIn)
    }

    @Test func aSessionTheServerEndedSignsOut() async throws {
        try await signIn(accessExpiresIn: 900)
        MockServer.start { request in
            .init(status: 401, json: #"{"message":"Session expired, please sign in again"}"#)
        }
        defer { MockServer.stop() }
        let expired = NotificationWaiter(.sessionExpired)

        await #expect(throws: APIError.self) { _ = try await ModerationService.shared.me() }

        #expect(MockServer.received("POST", "/auth/refresh").count == 1)
        #expect(!AuthService.shared.isSignedIn)
        #expect(await expired.posted())
    }

    @Test func goingOfflineDuringARenewalKeepsTheSession() async throws {
        try await signIn(accessExpiresIn: 5)
        MockServer.start { _ in throw URLError(.notConnectedToInternet) }
        defer { MockServer.stop() }

        await #expect(throws: (any Error).self) { _ = try await ModerationService.shared.me() }

        #expect(AuthService.shared.isSignedIn)
    }

    @Test func signingOutEndsTheSessionOnTheServer() async throws {
        try await signIn(accessExpiresIn: 900)
        MockServer.start { _ in .init(json: #"{"message":"Signed out"}"#) }
        defer { MockServer.stop() }

        AuthService.shared.signOut()

        #expect(!AuthService.shared.isSignedIn)
        for _ in 0..<50 where MockServer.received("POST", "/auth/logout").isEmpty {
            try await Task.sleep(for: .milliseconds(20))
        }
        let logout = try #require(MockServer.received("POST", "/auth/logout").first)
        #expect(logout.body.contains("refresh-1"))
    }

    @Test func requestsTellTheServerTheAppsLanguage() async throws {
        MockServer.start { _ in .init(json: #"{"contactEmail":null,"privacyPolicyUrl":null,"termsUrl":null}"#) }
        defer { MockServer.stop() }

        _ = try await SafetyService.shared.legalInfo()

        let request = try #require(MockServer.received.first)
        #expect(request.headers["Accept-Language"] == Bundle.main.preferredLocalizations.first)
        #expect(request.headers["Authorization"] == nil)
    }
}

/// Waits a little for a notification.
private final class NotificationWaiter: @unchecked Sendable {
    private var observer: NSObjectProtocol?
    private var didPost = false
    private let lock = NSLock()

    init(_ name: Notification.Name) {
        observer = NotificationCenter.default.addObserver(forName: name, object: nil, queue: nil) { [weak self] _ in
            self?.lock.withLock { self?.didPost = true }
        }
    }

    func posted() async -> Bool {
        for _ in 0..<50 {
            if lock.withLock({ didPost }) { return true }
            try? await Task.sleep(for: .milliseconds(20))
        }
        return false
    }

    deinit {
        if let observer { NotificationCenter.default.removeObserver(observer) }
    }
}
