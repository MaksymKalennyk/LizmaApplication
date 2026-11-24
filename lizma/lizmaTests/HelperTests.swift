import Foundation
import Testing
@testable import lizma

struct JWTTests {
    @Test func readsTheUsernameAndExpiry() throws {
        let token = makeAccessToken(username: "anna", expiresIn: 600)
        #expect(JWT.username(from: token) == "anna")
        let expiry = try #require(JWT.expirationDate(of: token))
        #expect(abs(expiry.timeIntervalSinceNow - 600) < 5)
    }

    @Test func ignoresGarbage() {
        #expect(JWT.username(from: "not-a-token") == nil)
        #expect(JWT.expirationDate(of: "a.b.c") == nil)
    }
}

struct UsernameValidatorTests {
    @Test(arguments: ["anna_k", "max.07", "liza-m", "Name_With.Dots-1"])
    func acceptsLettersDigitsAndSeparators(_ name: String) {
        #expect(UsernameValidator.hasOnlyAllowedCharacters(name))
    }

    @Test(arguments: ["anna k", "max!", "лиза", "a/b"])
    func rejectsEverythingElse(_ name: String) {
        #expect(!UsernameValidator.hasOnlyAllowedCharacters(name))
    }

    @Test func lengthLimits() {
        #expect(!UsernameValidator.lengthRange.contains(4))
        #expect(UsernameValidator.lengthRange.contains(5))
        #expect(UsernameValidator.lengthRange.contains(50))
        #expect(!UsernameValidator.lengthRange.contains(51))
    }
}

struct RelativeTimeTests {
    @Test func secondsAgoReadAsJustNow() {
        let now = Date()
        let string = ISO8601DateFormatter().string(from: now.addingTimeInterval(-20))
        #expect(RelativeTime.string(from: string, relativeTo: now) == String(localized: "just now"))
    }

    @Test func fractionalSecondsAreUnderstood() {
        #expect(RelativeTime.date(from: "2026-11-16T13:37:34.625804Z") != nil)
        #expect(RelativeTime.date(from: "2026-11-16T13:37:34Z") != nil)
        #expect(RelativeTime.string(from: "yesterday") == "yesterday")
    }
}

struct ReportGroupTests {
    private func report(_ id: Int64, type: ReportTargetType = .post, target: Int64 = 7, reason: ReportReason,
                        created: String, resolvedAt: String? = nil) -> AdminReport {
        AdminReport(id: id, targetType: type, targetId: target, targetUsername: "bob", targetPreview: "text",
                    targetArtworkUrl: nil, reason: reason, details: nil, reporterUsername: "ann",
                    status: resolvedAt == nil ? .open : .resolved, createdAt: created, resolvedAt: resolvedAt,
                    resolution: resolvedAt == nil ? nil : .removeContent, resolvedByUsername: nil, attachments: nil)
    }

    @Test func reportsAboutTheSameTargetAreOneGroup() {
        let groups = ReportGroup.groups(of: [
            report(1, reason: .spam, created: "2026-11-16T10:00:00Z"),
            report(2, type: .user, target: 3, reason: .hate, created: "2026-11-16T10:01:00Z"),
            report(3, reason: .spam, created: "2026-11-16T10:02:00Z"),
            report(4, reason: .harassment, created: "2026-11-16T10:03:00Z"),
        ])
        #expect(groups.count == 2)
        #expect(groups[0].reports.map(\.id) == [1, 3, 4])
        #expect(groups[0].first.id == 1)
        #expect(groups[0].reasonsSummary == "\(ReportReason.spam.shortLabel) ×2, \(ReportReason.harassment.shortLabel)")
        #expect(groups[1].targetType == .user)
    }

    @Test func handledReportsAreGroupedByDecision() {
        let groups = ReportGroup.groups(of: [
            report(1, reason: .spam, created: "2026-11-16T10:00:00Z", resolvedAt: "2026-11-16T11:00:00Z"),
            report(2, reason: .spam, created: "2026-11-16T10:05:00Z", resolvedAt: "2026-11-16T11:00:00Z"),
            report(3, reason: .spam, created: "2026-11-16T12:00:00Z", resolvedAt: "2026-11-16T13:00:00Z"),
        ])
        #expect(groups.map { $0.reports.count } == [2, 1])
        #expect(!groups[0].isOpen)
    }
}

struct PushRouteTests {
    @Test func eachNotificationLeadsToItsPlace() {
        #expect(PushRoute(userInfo: ["type": "friend_request", "username": "lizka"]) == .friendRequests)
        #expect(PushRoute(userInfo: ["type": "new_friend", "username": "lizka"]) == .profile(username: "lizka"))
        #expect(PushRoute(userInfo: ["type": "reaction", "postId": 68]) == .post(id: 68, showComments: false))
        #expect(PushRoute(userInfo: ["type": "comment", "postId": 68, "commentId": 7]) == .post(id: 68, showComments: true))
        #expect(PushRoute(userInfo: ["type": "reply", "postId": NSNumber(value: 68)]) == .post(id: 68, showComments: true))
        #expect(PushRoute(userInfo: ["type": "daily_reminder"]) == .composer)
    }

    @Test func somethingUnknownOrIncompleteLeadsNowhere() {
        #expect(PushRoute(userInfo: [:]) == nil)
        #expect(PushRoute(userInfo: ["type": "something_new"]) == nil)
        #expect(PushRoute(userInfo: ["type": "comment"]) == nil)
        #expect(PushRoute(userInfo: ["type": "new_friend"]) == nil)
        #expect(PushRoute(userInfo: ["type": "reaction", "postId": "68"]) == .post(id: 68, showComments: false))
    }

    @Test func onlyNewsAboutFriendsRefreshesTheRequests() {
        #expect(PushRoute.friendRequests.changesFriends)
        #expect(PushRoute.profile(username: "lizka").changesFriends)
        #expect(!PushRoute.composer.changesFriends)
        #expect(!PushRoute.post(id: 1, showComments: true).changesFriends)
    }
}

struct LanguageSettingsTests {
    /// Settings kept in a throwaway preferences domain, never the app's own language.
    private func withSettings(phone: [String], _ body: (LanguageSettings, UserDefaults) -> Void) {
        let name = "lizma.tests.language.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        var settings = LanguageSettings()
        settings.defaults = defaults
        settings.domain = name
        settings.systemLanguages = { phone }
        body(settings, defaults)
    }

    @Test func followsThePhoneUntilALanguageIsChosen() {
        withSettings(phone: ["en-US", "uk-UA"]) { settings, _ in
            #expect(settings.choice == .system)
            #expect(settings.code == "en")
        }
        withSettings(phone: ["uk-UA", "en-US"]) { settings, _ in
            #expect(settings.code == "uk")
        }
        withSettings(phone: ["de-DE", "uk-UA"]) { settings, _ in
            #expect(settings.code == "uk") // the first of the phone's languages that Lizma has
        }
    }

    @Test func theChoiceIsTheAppsOwnLanguageSettingAndCanBeUndone() {
        withSettings(phone: ["en-US"]) { settings, defaults in
            settings.choose(.ukrainian)
            #expect(settings.choice == .ukrainian)
            #expect(settings.code == "uk")
            #expect(defaults.stringArray(forKey: "AppleLanguages") == ["uk"])

            settings.choose(.system)
            #expect(settings.choice == .system)
            #expect(settings.code == "en")
            #expect(defaults.persistentDomain(forName: settings.domain)?["AppleLanguages"] == nil)
        }
    }

    @Test func textsComeFromTheChosenLanguage() {
        #expect(String(localized: "Save", bundle: LanguageSettings.bundle(for: "uk")) == "Зберегти")
        #expect(String(localized: "Save", bundle: LanguageSettings.bundle(for: "en")) == "Save")
        #expect(String(localized: "composer.publish", defaultValue: "Post", bundle: LanguageSettings.bundle(for: "en")) == "Post")
        #expect(AppLanguage.endonym(of: "uk") == "Українська")
        #expect(LanguageSettings.locale(for: "uk").language.languageCode?.identifier == "uk")
    }
}
