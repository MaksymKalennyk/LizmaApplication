import SwiftUI

/// Reports about the same post, comment or user, handled with one decision.
struct ReportGroup: Identifiable, Hashable {
    let id: String
    /// Oldest first.
    let reports: [AdminReport]

    var first: AdminReport { reports[0] }
    var targetType: ReportTargetType { first.targetType }
    var isOpen: Bool { first.status == .open }

    /// "Post by anna", "Comment by bob", "User clara".
    var title: String {
        let who = first.targetUsername ?? String(localized: "a deleted user", bundle: .app)
        switch targetType {
        case .post: return String(localized: "Post by \(who)", bundle: .app)
        case .comment: return String(localized: "Comment by \(who)", bundle: .app)
        case .user: return String(localized: "User \(who)", bundle: .app)
        }
    }

    var systemImage: String {
        switch targetType {
        case .post: return "music.note"
        case .comment: return "text.bubble"
        case .user: return "person"
        }
    }

    /// "Spam ×2, Harassment".
    var reasonsSummary: String {
        var counts: [(ReportReason, Int)] = []
        for report in reports {
            if let index = counts.firstIndex(where: { $0.0 == report.reason }) {
                counts[index].1 += 1
            } else {
                counts.append((report.reason, 1))
            }
        }
        return counts.map { $0.1 > 1 ? "\($0.0.shortLabel) ×\($0.1)" : $0.0.shortLabel }.joined(separator: ", ")
    }

    /// Open reports are grouped by what they are about; handled ones by the decision that closed them.
    static func groups(of reports: [AdminReport]) -> [ReportGroup] {
        var order: [String] = []
        var byKey: [String: [AdminReport]] = [:]
        for report in reports {
            let key = "\(report.targetType.rawValue)-\(report.targetId)-\(report.resolvedAt ?? "")"
            if byKey[key] == nil { order.append(key) }
            byKey[key, default: []].append(report)
        }
        return order.map { key in
            ReportGroup(id: key, reports: byKey[key]!.sorted { $0.createdAt < $1.createdAt })
        }
    }
}

extension ReportReason {
    /// For lists, where the full label ("Spam or scam") is too long.
    var shortLabel: String {
        switch self {
        case .spam: return String(localized: "Spam", bundle: .app)
        case .harassment: return String(localized: "Harassment", bundle: .app)
        case .hate: return String(localized: "Hate", bundle: .app)
        case .nudity: return String(localized: "Nudity", bundle: .app)
        case .violence: return String(localized: "Violence", bundle: .app)
        case .other: return String(localized: "Other", bundle: .app, comment: "A report reason")
        }
    }
}

extension ReportAction {
    var pastTense: String {
        switch self {
        case .removeContent: return String(localized: "Content removed", bundle: .app)
        case .banUser: return String(localized: "Author suspended", bundle: .app)
        case .dismiss: return String(localized: "Dismissed", bundle: .app)
        }
    }
}

struct ModerationReportsScreen: View {
    @State private var showHandled = false
    @State private var groups: [ReportGroup] = []
    @State private var hasLoaded = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Picker("Show", selection: $showHandled) {
                    Text("Open").tag(false)
                    Text("Handled").tag(true)
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            if let errorMessage, groups.isEmpty {
                ContentUnavailableView {
                    Label("Couldn't load reports", systemImage: "exclamationmark.triangle")
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
            } else if groups.isEmpty {
                ContentUnavailableView(showHandled ? "Nothing handled yet" : "No open reports",
                                       systemImage: showHandled ? "tray" : "checkmark.seal",
                                       description: Text(showHandled ? "Decisions show up here." : "All caught up."))
                    .listRowBackground(Color.clear)
            } else {
                Section {
                    ForEach(groups) { group in
                        NavigationLink {
                            ReportGroupScreen(group: group, onDecision: { Task { await load() } })
                        } label: {
                            ReportGroupRow(group: group)
                        }
                    }
                }
            }
        }
        .navigationTitle("Reports")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: showHandled) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        let handled = showHandled
        do {
            let reports = try await ModerationService.shared.reports(handled: handled)
            guard handled == showHandled else { return }
            groups = ReportGroup.groups(of: reports)
            errorMessage = nil
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
            groups = []
        }
        hasLoaded = true
    }
}

private struct ReportGroupRow: View {
    let group: ReportGroup

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ReportTargetThumbnail(group: group, size: 44)

            VStack(alignment: .leading, spacing: 3) {
                Text(group.title)
                    .font(.body.weight(.medium))
                    .lineLimit(1)
                    .truncationMode(.middle)
                if group.targetType != .user {
                    Text(group.first.targetPreview ?? "Already deleted")
                        .font(.subheadline)
                        .foregroundStyle(group.first.targetPreview == nil ? HierarchicalShapeStyle.tertiary : .secondary)
                        .lineLimit(2)
                }
                Group {
                    if let resolution = group.first.resolution {
                        Text("\(resolution.pastTense) · \(RelativeTime.string(from: group.first.resolvedAt ?? group.first.createdAt))")
                    } else {
                        Text("\(group.reasonsSummary) · \(RelativeTime.string(from: group.reports.last!.createdAt))")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

/// The cover of a reported post, the avatar of a reported user, or an icon.
private struct ReportTargetThumbnail: View {
    let group: ReportGroup
    let size: CGFloat

    var body: some View {
        if group.targetType == .user, let username = group.first.targetUsername {
            AvatarView(username: username, imageURL: group.first.targetArtworkUrl, size: size)
        } else if group.targetType == .post, let url = group.first.targetArtworkUrl.flatMap(URL.init(string:)) {
            RemoteImage(url: url) { image in
                image.resizable().scaledToFill()
            } placeholder: { _ in
                icon
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
        } else {
            icon
        }
    }

    private var icon: some View {
        RoundedRectangle(cornerRadius: size * 0.18, style: .continuous)
            .fill(Color(.tertiarySystemFill))
            .frame(width: size, height: size)
            .overlay(Image(systemName: group.systemImage).foregroundStyle(.secondary))
    }
}

/// Everything about one reported post, comment or user, and the decision.
struct ReportGroupScreen: View {
    let group: ReportGroup
    let onDecision: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pendingAction: ReportAction?
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                HStack(alignment: .top, spacing: 14) {
                    ReportTargetThumbnail(group: group, size: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(group.title)
                            .font(.headline)
                        if group.targetType == .user {
                            // The preview of a user is their name, already in the title.
                            EmptyView()
                        } else if let preview = group.first.targetPreview {
                            Text(preview)
                                .textSelection(.enabled)
                        } else {
                            Text("This content was already deleted.")
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            Section("\(group.reports.count) reports") {
                ForEach(group.reports) { report in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.reason.label)
                            .font(.body.weight(.medium))
                        if let details = report.details {
                            Text(details)
                                .textSelection(.enabled)
                        }
                        Text("by \(report.reporterUsername) · \(RelativeTime.string(from: report.createdAt))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }

            if group.isOpen {
                actions
            } else if let resolution = group.first.resolution {
                Section("Decision") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(resolution.pastTense)
                        Text("by \(group.first.resolvedByUsername ?? String(localized: "a former moderator", bundle: .app)) · \(RelativeTime.string(from: group.first.resolvedAt ?? group.first.createdAt))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(group.targetType == .user ? "User report" : "Report")
        .navigationBarTitleDisplayMode(.inline)
        .disabled(isWorking)
        .overlay {
            if isWorking { ProgressView() }
        }
    }

    @ViewBuilder
    private var actions: some View {
        Section {
            if group.targetType != .user {
                decisionButton(.removeContent, title: group.targetType == .post ? "Remove post" : "Remove comment")
                    .disabled(group.first.targetPreview == nil)
            }
            if let author = group.first.targetUsername {
                decisionButton(.banUser, title: "Suspend \(author)")
            }
            decisionButton(.dismiss, title: "Dismiss: no violation")
        } header: {
            Text("Decision")
        } footer: {
            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            } else {
                switch group.targetType {
                case .user: Text("The decision closes every open report about this user.")
                case .post: Text("The decision closes every open report about this post.")
                case .comment: Text("The decision closes every open report about this comment.")
                }
            }
        }
    }

    /// A decision with its own confirmation, which comes out of the button that was tapped.
    private func decisionButton(_ action: ReportAction, title: String) -> some View {
        Button(title, role: action == .dismiss ? nil : .destructive) {
            pendingAction = action
        }
        .confirmationDialog(confirmationTitle,
                            isPresented: Binding(get: { pendingAction == action },
                                                 set: { if !$0, pendingAction == action { pendingAction = nil } }),
                            titleVisibility: .visible) {
            Button(confirmationButton(for: action), role: action == .dismiss ? nil : .destructive) {
                Task { await decide(action) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(confirmationMessage(for: action))
        }
    }

    private var confirmationTitle: String {
        switch pendingAction {
        case .removeContent: return group.targetType == .post ? String(localized: "Remove this post?", bundle: .app) : String(localized: "Remove this comment?", bundle: .app)
        case .banUser: return String(localized: "Suspend \(group.first.targetUsername ?? String(localized: "this user"))?", bundle: .app)
        case .dismiss, nil: return String(localized: "Dismiss the report?", bundle: .app)
        }
    }

    private func confirmationButton(for action: ReportAction) -> String {
        switch action {
        case .removeContent: return group.targetType == .post ? String(localized: "Remove post", bundle: .app) : String(localized: "Remove comment", bundle: .app)
        case .banUser: return String(localized: "Suspend", bundle: .app)
        case .dismiss: return String(localized: "Dismiss", bundle: .app)
        }
    }

    private func confirmationMessage(for action: ReportAction) -> String {
        switch action {
        case .removeContent:
            return String(localized: "It's deleted for everyone, with its comments and reactions.", bundle: .app)
        case .banUser:
            return String(localized: "They can't sign in anymore, and everything they posted and their friendships are removed. It can be lifted later in Users, but the content doesn't come back.", bundle: .app)
        case .dismiss:
            return String(localized: "Nothing is removed.", bundle: .app)
        }
    }

    private func decide(_ action: ReportAction) async {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            try await ModerationService.shared.resolve(reportId: group.first.id, action: action)
            await ModerationStore.shared.refresh()
            onDecision()
            dismiss()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}
