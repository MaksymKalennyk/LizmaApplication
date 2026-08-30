import SwiftUI

struct RequestsScreen: View {
    @StateObject private var vm = RequestsViewModel()
    @State private var recipientIdText = ""

    var body: some View {
        NavigationStack {
            VStack {
                HStack {
                    TextField("ID пользователя", text: $recipientIdText)
                        .keyboardType(.numberPad)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(.thinMaterial))
                    Button("Отправить") {
                        Task {
                            if let id = Int64(recipientIdText) {
                                try? await FriendService.shared.sendRequest(to: id)
                                await vm.reload()
                                recipientIdText = ""
                            }
                        }
                    }.buttonStyle(.borderedProminent)
                }.padding(.horizontal)

                List {
                    Section("Входящие") {
                        ForEach(vm.received) { req in
                            RequestRow(req: req, accept: { Task { await vm.accept(req) } },
                                       reject: { Task { await vm.reject(req) } })
                        }
                    }
                    Section("Исходящие") {
                        ForEach(vm.sent) { req in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("→ \(req.recipientUsername)")
                                    Text(req.status).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                            }
                        }
                    }
                }.listStyle(.insetGrouped)
            }
            .navigationTitle("Заявки")
        }
        .task { await vm.reload() }
    }
}

private struct RequestRow: View {
    let req: FriendRequestDto
    let accept: () -> Void
    let reject: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("от \(req.requesterUsername)")
                Text(req.status).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: 8) {
                Button("Принять", action: accept).buttonStyle(.borderedProminent)
                Button("Отклонить", action: reject).buttonStyle(.bordered)
            }
        }
    }
}
