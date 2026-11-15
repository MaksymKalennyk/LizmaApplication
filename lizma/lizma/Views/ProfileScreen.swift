import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @StateObject private var requestsVM = RequestsViewModel()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    MusicAccountsStatusRow()
                }
                Section("Account") {
                    Button(role: .destructive) {
                        auth.signOut()
                    } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }

                Section("Incoming requests") {
                    if requestsVM.loading && !requestsVM.hasLoaded {
                        ProgressView()
                    } else if requestsVM.received.isEmpty {
                        Text("No incoming requests").foregroundStyle(.secondary)
                    } else {
                        ForEach(requestsVM.received) { req in
                            RequestRow(req: req,
                                       isBusy: requestsVM.inFlight.contains(req.id),
                                       accept: { Task { await requestsVM.accept(req) } },
                                       reject: { Task { await requestsVM.reject(req) } })
                        }
                    }
                }

                Section("Sent requests") {
                    if requestsVM.loading && !requestsVM.hasLoaded {
                        ProgressView()
                    } else if requestsVM.sent.isEmpty {
                        Text("No pending sent requests").foregroundStyle(.secondary)
                    } else {
                        ForEach(requestsVM.sent) { req in
                            VStack(alignment: .leading) {
                                Text("→ \(req.recipientUsername)")
                                Text(req.status).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                if let err = requestsVM.error {
                    Section {
                        Text(err).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Profile")
            .refreshable {
                await requestsVM.reload()
                await MusicStatusStore.shared.refresh()
            }
        }
        .task { await requestsVM.reload() }
        .onReceive(NotificationCenter.default.publisher(for: .friendsChanged)) { _ in
            Task { await requestsVM.reload() }
        }
    }
}

private struct RequestRow: View {
    let req: FriendRequestDto
    let isBusy: Bool
    let accept: () -> Void
    let reject: () -> Void

    var body: some View {
        HStack {
            // Incoming requests are always pending, so only the name is shown.
            Text(req.requesterUsername)
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer(minLength: 8)
            if isBusy {
                ProgressView()
            }
            HStack(spacing: 8) {
                Button("Accept", action: accept).buttonStyle(.borderedProminent)
                Button("Decline", action: reject).buttonStyle(.bordered)
            }
            .controlSize(.small)
            .lineLimit(1)
            .fixedSize()
            .disabled(isBusy)
        }
    }
}
