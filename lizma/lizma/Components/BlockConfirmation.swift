import SwiftUI

/// Someone the user may block.
struct BlockTarget: Identifiable, Equatable {
    let userId: Int64
    let username: String
    var id: Int64 { userId }
}

extension View {
    /// Asks "Block <name>?" whenever `target` is set. On confirmation it blocks the user on the server,
    /// calls `onBlocked` and posts `.friendsChanged` (blocking also ends the friendship).
    func blockConfirmation(target: Binding<BlockTarget?>,
                           onBlocked: @escaping (BlockTarget) -> Void = { _ in }) -> some View {
        modifier(BlockConfirmationModifier(target: target, onBlocked: onBlocked))
    }
}

private struct BlockConfirmationModifier: ViewModifier {
    @Binding var target: BlockTarget?
    let onBlocked: (BlockTarget) -> Void

    @State private var failure: String?

    private enum Prompt {
        case confirm(BlockTarget)
        case failed(String)
    }

    private var prompt: Prompt? {
        if let failure { return .failed(failure) }
        if let target { return .confirm(target) }
        return nil
    }

    private var title: String {
        switch prompt {
        case .confirm(let blocked)?: return "Block \(blocked.username)?"
        case .failed?: return "Couldn't block user"
        case nil: return ""
        }
    }

    private var isPresented: Binding<Bool> {
        Binding(get: { prompt != nil }, set: { presented in
            if !presented {
                target = nil
                failure = nil
            }
        })
    }

    func body(content: Content) -> some View {
        content.alert(title, isPresented: isPresented, presenting: prompt) { prompt in
            switch prompt {
            case .confirm(let blocked):
                Button("Block", role: .destructive) {
                    Task { await block(blocked) }
                }
                Button("Cancel", role: .cancel) {}
            case .failed:
                Button("OK", role: .cancel) {}
            }
        } message: { prompt in
            switch prompt {
            case .confirm:
                Text("You won't see each other's posts, and you'll be removed from each other's friends.")
            case .failed(let message):
                Text(message)
            }
        }
    }

    private func block(_ blocked: BlockTarget) async {
        do {
            try await SafetyService.shared.block(userId: blocked.userId)
            onBlocked(blocked)
            NotificationCenter.default.post(name: .friendsChanged, object: nil)
        } catch {
            guard !error.isCancellation else { return }
            failure = error.userMessage
        }
    }
}
