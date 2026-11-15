import SwiftUI

/// Something the user may report.
struct ReportTarget: Identifiable {
    let type: ReportTargetType
    let targetId: Int64
    var id: String { "\(type.rawValue)-\(targetId)" }

    var title: String {
        switch type {
        case .post: return "Report post"
        case .comment: return "Report comment"
        case .user: return "Report user"
        }
    }

    fileprivate var question: String {
        switch type {
        case .post: return "Why are you reporting this post?"
        case .comment: return "Why are you reporting this comment?"
        case .user: return "Why are you reporting this user?"
        }
    }
}

struct ReportSheet: View {
    let target: ReportTarget

    @Environment(\.dismiss) private var dismiss
    @State private var reason: ReportReason?
    @State private var details = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    /// The server's thank-you message, shown once the report went through.
    @State private var confirmation: String?

    /// The server's limit, counted like Java's `String.length()` (UTF-16 units).
    private static let maxDetailsLength = 500

    var body: some View {
        NavigationStack {
            Form {
                Section(target.question) {
                    ForEach(ReportReason.allCases) { option in
                        Button {
                            reason = option
                        } label: {
                            HStack {
                                // `Color.primary`, not `.primary`: inside a button the latter is the tint.
                                Text(option.label)
                                    .foregroundStyle(Color.primary)
                                Spacer()
                                if reason == option {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.tint)
                                }
                            }
                        }
                        .accessibilityAddTraits(reason == option ? .isSelected : [])
                    }
                }

                Section {
                    TextField("Add details (optional)", text: $details, axis: .vertical)
                        .lineLimit(3...6)
                } footer: {
                    Text("\(details.utf16.count)/\(Self.maxDetailsLength)")
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle(target.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSubmitting)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await submit() }
                    } label: {
                        if isSubmitting {
                            ProgressView()
                        } else {
                            Text("Submit")
                        }
                    }
                    .disabled(reason == nil || isSubmitting)
                }
            }
            .onChange(of: details) { _, newValue in
                let limited = Self.limited(newValue)
                if limited != newValue { details = limited }
            }
            .alert("Report sent", isPresented: isShowingConfirmation) {
                Button("OK") { dismiss() }
            } message: {
                Text(confirmation ?? "")
            }
            .interactiveDismissDisabled(isSubmitting)
        }
    }

    private var isShowingConfirmation: Binding<Bool> {
        Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } })
    }

    private static func limited(_ text: String) -> String {
        var result = text
        while result.utf16.count > maxDetailsLength {
            result.removeLast()
        }
        return result
    }

    private func submit() async {
        guard let reason, !isSubmitting else { return }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        let trimmed = details.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = ReportRequest(targetType: target.type,
                                    targetId: target.targetId,
                                    reason: reason,
                                    details: trimmed.isEmpty ? nil : Self.limited(trimmed))
        do {
            confirmation = try await SafetyService.shared.report(request)
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }
}
