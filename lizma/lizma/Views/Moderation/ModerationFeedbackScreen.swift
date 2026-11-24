import SwiftUI

/// The feedback inbox: new messages first, done ones on the second tab.
struct ModerationFeedbackScreen: View {
    @State private var showDone = false
    @State private var items: [FeedbackItem] = []
    @State private var hasLoaded = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Picker("Show", selection: $showDone) {
                    Text("New").tag(false)
                    Text(String(localized: "feedback.tab.done", defaultValue: "Done", bundle: .app, comment: "Tab with feedback marked as done")).tag(true)
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            if let errorMessage, items.isEmpty {
                ContentUnavailableView {
                    Label("Couldn't load feedback", systemImage: "exclamationmark.triangle")
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
            } else if items.isEmpty {
                ContentUnavailableView(showDone ? "Nothing done yet" : "No new feedback",
                                       systemImage: showDone ? "tray" : "envelope.open",
                                       description: Text(showDone ? "Messages you mark as done show up here." : "All caught up."))
                    .listRowBackground(Color.clear)
            } else {
                Section {
                    ForEach(items) { item in
                        NavigationLink {
                            FeedbackDetailScreen(item: item, onChange: { Task { await load() } })
                        } label: {
                            FeedbackRow(item: item)
                        }
                    }
                }
            }
        }
        .navigationTitle("Feedback")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: showDone) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        let done = showDone
        do {
            let loaded = try await ModerationService.shared.feedback(done: done)
            guard done == showDone else { return }
            items = loaded
            errorMessage = nil
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
            items = []
        }
        hasLoaded = true
    }
}

private struct FeedbackRow: View {
    let item: FeedbackItem

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.category.systemImage)
                .foregroundStyle(.secondary)
                .frame(width: 22)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.message)
                    .lineLimit(3)
                HStack(spacing: 4) {
                    Text("\(item.category.label) · \(item.authorUsername) · \(RelativeTime.string(from: item.createdAt))")
                        .lineLimit(1)
                        .truncationMode(.middle)
                    if item.contactEmail != nil {
                        Image(systemName: "envelope.fill")
                            .accessibilityLabel("Left an email")
                    }
                    if let count = item.attachments?.count, count > 0 {
                        Image(systemName: "photo")
                            .accessibilityLabel("With screenshots")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

struct FeedbackDetailScreen: View {
    let item: FeedbackItem
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Text(item.message)
                    .textSelection(.enabled)
                    .padding(.vertical, 4)
            } header: {
                Label(item.category.label, systemImage: item.category.systemImage)
            }

            if let screenshots = item.attachments, !screenshots.isEmpty {
                Section("Screenshots") {
                    ScreenshotStrip(paths: screenshots)
                        .padding(.vertical, 4)
                }
            }

            Section {
                LabeledContent("From", value: item.authorUsername)
                LabeledContent("Sent", value: RelativeTime.string(from: item.createdAt))
                if let appVersion = item.appVersion {
                    LabeledContent("App", value: appVersion)
                }
                if let osVersion = item.osVersion {
                    LabeledContent("System", value: osVersion)
                }
                if let handledBy = item.handledByUsername, let handledAt = item.handledAt {
                    LabeledContent(String(localized: "feedback.handledBy", defaultValue: "Done", bundle: .app, comment: "Who marked the feedback as done, and when"), value: "\(handledBy) · \(RelativeTime.string(from: handledAt))")
                }
            }

            if let email = item.contactEmail {
                Section {
                    Button {
                        if let url = Self.replyURL(to: email) { openURL(url) }
                    } label: {
                        Label("Reply to \(email)", systemImage: "arrowshape.turn.up.left")
                    }
                } footer: {
                    Text("Opens Mail. The notification email has this address as Reply-To as well.")
                }
            }

            Section {
                Button(item.status == .new ? "Mark as done" : "Move back to new") {
                    Task { await toggleDone() }
                }
            } footer: {
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Feedback")
        .navigationBarTitleDisplayMode(.inline)
        .disabled(isWorking)
        .overlay {
            if isWorking { ProgressView() }
        }
    }

    private static func replyURL(to email: String) -> URL? {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = email
        components.queryItems = [URLQueryItem(name: "subject", value: String(localized: "Re: your Lizma feedback", bundle: .app))]
        return components.url
    }

    private func toggleDone() async {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            try await ModerationService.shared.markFeedback(id: item.id, done: item.status == .new)
            await ModerationStore.shared.refresh()
            onChange()
            dismiss()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}
