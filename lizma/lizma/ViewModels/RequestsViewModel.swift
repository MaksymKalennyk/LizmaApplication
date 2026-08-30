import Foundation

@MainActor
final class RequestsViewModel: ObservableObject {
    @Published var received: [FriendRequestDto] = []
    @Published var sent: [FriendRequestDto] = []
    @Published var loading = false
    @Published var error: String? = nil

    func reload() async {
        loading = true
        error = nil
        do {
            received = try await FriendService.shared.listReceivedRequests()
            sent = []
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
        loading = false
    }

    func accept(_ request: FriendRequestDto) async {
        await respond(request, status: FriendRequestStatus.accepted)
    }

    func reject(_ request: FriendRequestDto) async {
        await respond(request, status: FriendRequestStatus.declined)
    }

    private func respond(_ request: FriendRequestDto, status: FriendRequestStatus) async {
        do {
            try await FriendService.shared.respond(to: request.id, status: status)
            await reload()
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }
}
