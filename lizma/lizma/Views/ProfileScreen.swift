import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @State private var token: String = ""
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

                Section("Friend requests") {
                    if requestsVM.loading {
                        ProgressView()
                    } else {
                        if !requestsVM.received.isEmpty {
                            ForEach(requestsVM.received) { req in
                                RequestRow(req: req,
                                           accept: { Task { await requestsVM.accept(req) } },
                                           reject: { Task { await requestsVM.reject(req) } })
                            }
                        } else {
                            Text("No incoming requests").foregroundStyle(.secondary)
                        }

                        if !requestsVM.sent.isEmpty {
                            Section("Sent") {
                                ForEach(requestsVM.sent) { req in
                                    HStack {
                                        VStack(alignment: .leading) {
                                            Text("→ \(req.recipientUsername)")
                                            Text(req.status).font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                }
                            }
                        }
                    }
                    if let err = requestsVM.error { Text(err).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Profile")
        }
        .task { await requestsVM.reload() }
    }
}

private struct RequestRow: View {
    let req: FriendRequestDto
    let accept: () -> Void
    let reject: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("from \(req.requesterUsername)")
                Text(req.status).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: 8) {
                Button("Accept", action: accept).buttonStyle(.borderedProminent)
                Button("Decline", action: reject).buttonStyle(.bordered)
            }
        }
    }
}
