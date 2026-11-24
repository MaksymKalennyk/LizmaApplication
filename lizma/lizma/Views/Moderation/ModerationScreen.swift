import SwiftUI

/// Profile → Moderation, only for moderators: what waits for them and the tools to handle it.
struct ModerationScreen: View {
    @ObservedObject private var moderation = ModerationStore.shared

    var body: some View {
        List {
            Section {
                NavigationLink {
                    ModerationReportsScreen()
                } label: {
                    HStack {
                        Label("Reports", systemImage: "exclamationmark.bubble")
                        Spacer(minLength: 8)
                        CountBadge(count: moderation.summary?.openReports ?? 0)
                    }
                }

                NavigationLink {
                    ModerationFeedbackScreen()
                } label: {
                    HStack {
                        Label("Feedback", systemImage: "envelope")
                        Spacer(minLength: 8)
                        CountBadge(count: moderation.summary?.newFeedback ?? 0)
                    }
                }
            } footer: {
                Text("Handle reports within 24 hours. New reports and feedback are also emailed to the support mailbox.")
            }

            Section {
                NavigationLink {
                    ModerationUsersScreen()
                } label: {
                    Label("Users", systemImage: "person.2")
                }

                NavigationLink {
                    ModerationLogScreen()
                } label: {
                    Label("Activity", systemImage: "clock.arrow.circlepath")
                }
            }
        }
        .navigationTitle("Moderation")
        .task { await moderation.refresh() }
        .refreshable { await moderation.refresh() }
    }
}

/// What moderators did, newest first.
struct ModerationLogScreen: View {
    @State private var entries: [ModerationLogEntry] = []
    @State private var hasLoaded = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            if let errorMessage, entries.isEmpty {
                ContentUnavailableView {
                    Label("Couldn't load activity", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button("Retry") { Task { await load() } }
                        .buttonStyle(.bordered)
                }
                .listRowBackground(Color.clear)
            } else if !hasLoaded {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
            } else if entries.isEmpty {
                ContentUnavailableView("Nothing yet", systemImage: "clock.arrow.circlepath",
                                       description: Text("Decisions on reports and users show up here."))
                    .listRowBackground(Color.clear)
            } else {
                ForEach(entries) { entry in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Image(systemName: Self.icon(for: entry.action))
                            .foregroundStyle(.secondary)
                            .frame(width: 22)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Self.sentence(for: entry))
                            Text(RelativeTime.string(from: entry.createdAt))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .navigationTitle("Activity")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            entries = try await ModerationService.shared.log()
            errorMessage = nil
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
        hasLoaded = true
    }

    static func sentence(for entry: ModerationLogEntry) -> String {
        let who = entry.moderatorUsername ?? String(localized: "A former moderator", bundle: .app)
        let whom = entry.targetUsername ?? String(localized: "a deleted user", bundle: .app)
        switch entry.action {
        case .removeContent: return String(localized: "\(who) removed content by \(whom)", bundle: .app)
        case .banUser: return String(localized: "\(who) suspended \(whom)", bundle: .app)
        case .unbanUser: return String(localized: "\(who) lifted the suspension of \(whom)", bundle: .app)
        case .deleteUser: return String(localized: "\(who) deleted the account \(whom)", bundle: .app)
        case .dismissReport: return String(localized: "\(who) dismissed a report about \(whom)", bundle: .app)
        }
    }

    private static func icon(for action: ModerationActionType) -> String {
        switch action {
        case .removeContent: return "trash"
        case .banUser: return "hand.raised"
        case .unbanUser: return "hand.raised.slash"
        case .deleteUser: return "person.crop.circle.badge.xmark"
        case .dismissReport: return "checkmark.circle"
        }
    }
}
