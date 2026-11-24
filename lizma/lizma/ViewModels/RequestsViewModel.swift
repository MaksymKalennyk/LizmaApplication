import Foundation

@MainActor
final class RequestsViewModel: ObservableObject {
    @Published var received: [FriendRequestDto] = []
    @Published var sent: [FriendRequestDto] = []
    @Published var loading = false
    /// True after the first successful load (later reloads keep the lists on screen).
    @Published private(set) var hasLoaded = false
    @Published var error: String? = nil
    /// Requests with an accept/decline call in flight; their buttons are disabled.
    @Published private(set) var inFlight: Set<Int64> = []

    func reload() async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        do {
            async let receivedRequests = FriendService.shared.listReceivedRequests()
            async let sentRequests = FriendService.shared.listSentRequests()
            let (newReceived, newSent) = try await (receivedRequests, sentRequests)
            received = newReceived
            sent = newSent
            error = nil
            hasLoaded = true
        } catch {
            guard !error.isCancellation else { return }
            self.error = error.userMessage
        }
    }

    func accept(_ request: FriendRequestDto) async {
        await respond(request, status: FriendRequestStatus.accepted)
    }

    func reject(_ request: FriendRequestDto) async {
        await respond(request, status: FriendRequestStatus.declined)
    }

    /// The user takes back a request they sent.
    func cancel(_ request: FriendRequestDto) async {
        guard !inFlight.contains(request.id) else { return }
        inFlight.insert(request.id)
        defer { inFlight.remove(request.id) }
        do {
            try await FriendService.shared.cancelRequest(request.id)
            error = nil
            sent.removeAll { $0.id == request.id }
        } catch {
            guard !error.isCancellation else { return }
            self.error = error.userMessage
        }
        await reload()
    }

    private func respond(_ request: FriendRequestDto, status: FriendRequestStatus) async {
        guard !inFlight.contains(request.id) else { return }
        inFlight.insert(request.id)
        defer { inFlight.remove(request.id) }
        do {
            try await FriendService.shared.respond(to: request.id, status: status)
            error = nil
            received.removeAll { $0.id == request.id }
            if status == .accepted {
                // Friends list and feed now include this person.
                NotificationCenter.default.post(name: .friendsChanged, object: nil)
            }
        } catch {
            guard !error.isCancellation else { return }
            self.error = error.userMessage
        }
        await reload()
    }
}
