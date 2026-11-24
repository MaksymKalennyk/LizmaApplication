import SwiftUI
import UIKit

/// Profile → Send feedback: an idea, a problem or anything else, straight to the team.
struct FeedbackFormView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var category: FeedbackCategory = .idea
    @State private var message = ""
    @State private var email = ""
    @State private var screenshots: [UIImage] = []
    @State private var isSending = false
    @State private var errorMessage: String?
    /// The server's thank-you message, shown once the feedback went through.
    @State private var confirmation: String?

    /// The server's limits, counted like Java's `String.length()` (UTF-16 units).
    private static let maxMessageLength = 2000
    private static let maxEmailLength = 254

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Topic", selection: $category) {
                        ForEach(FeedbackCategory.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                    // The control is a card of its own; another card around it is one too many.
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                Section {
                    TextField(placeholder, text: $message, axis: .vertical)
                        .lineLimit(5...12)
                } footer: {
                    // Only when it starts to matter.
                    if message.utf16.count > Self.maxMessageLength - 200 {
                        Text("\(message.utf16.count)/\(Self.maxMessageLength)")
                    }
                }

                Section {
                    ScreenshotPicker(images: $screenshots)
                } footer: {
                    Text("Up to 3. Only moderators see them.")
                }

                Section {
                    TextField("Email (optional)", text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } footer: {
                    Text("Leave your email if you'd like an answer. We use it only to reply to you.")
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }

                Section {
                } footer: {
                    Text("Your username, Lizma \(Self.appVersion ?? "") and \(Self.osVersion) are sent with your message.")
                }
            }
            .navigationTitle(String(localized: "feedback.form.title", defaultValue: "Send feedback", bundle: .app, comment: "Title of the feedback form, between Cancel and Send"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSending)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await send() }
                    } label: {
                        if isSending {
                            ProgressView()
                        } else {
                            Text("Send")
                        }
                    }
                    .disabled(trimmedMessage.isEmpty || isSending)
                }
            }
            .onChange(of: message) { _, newValue in
                let limited = Self.limited(newValue, to: Self.maxMessageLength)
                if limited != newValue { message = limited }
            }
            .onChange(of: email) { _, newValue in
                let limited = Self.limited(newValue, to: Self.maxEmailLength)
                if limited != newValue { email = limited }
            }
            .alert("Message sent", isPresented: isShowingConfirmation) {
                Button("OK") { dismiss() }
            } message: {
                Text(confirmation ?? "")
            }
            .interactiveDismissDisabled(isSending || !trimmedMessage.isEmpty || !screenshots.isEmpty)
        }
    }

    private var placeholder: String {
        switch category {
        case .idea: return String(localized: "What would make Lizma better?", bundle: .app)
        case .problem: return String(localized: "What went wrong? What did you expect to happen?", bundle: .app)
        case .other: return String(localized: "Your message", bundle: .app)
        }
    }

    private var trimmedMessage: String {
        message.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isShowingConfirmation: Binding<Bool> {
        Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } })
    }

    private static var appVersion: String? {
        guard let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String else { return nil }
        if let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String {
            return "\(version) (\(build))"
        }
        return version
    }

    private static var osVersion: String {
        "iOS \(UIDevice.current.systemVersion)"
    }

    private static func limited(_ text: String, to maxLength: Int) -> String {
        var result = text
        while result.utf16.count > maxLength {
            result.removeLast()
        }
        return result
    }

    private func send() async {
        guard !trimmedMessage.isEmpty, !isSending else { return }
        isSending = true
        errorMessage = nil
        defer { isSending = false }

        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        var request = FeedbackRequest(category: category,
                                      message: trimmedMessage,
                                      contactEmail: trimmedEmail.isEmpty ? nil : trimmedEmail,
                                      appVersion: Self.appVersion,
                                      osVersion: Self.osVersion)
        do {
            request.attachmentIds = try await AttachmentService.shared.upload(screenshots)
            confirmation = try await ModerationService.shared.sendFeedback(request)
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}

#Preview {
    FeedbackFormView()
}
