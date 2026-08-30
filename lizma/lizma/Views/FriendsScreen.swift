import SwiftUI

struct FriendsScreen: View {
    @StateObject private var vm = FriendsViewModel()

    @State private var query: String = ""
    @State private var results: [UserDto] = []
    @State private var searching = false
    @State private var sendError: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var selfUsername: String? = nil

    @State private var received: [FriendRequestDto] = []
    @State private var sent: [FriendRequestDto] = []

    @State private var sendingIDs: Set<Int64> = []
    @State private var addedUserIDs: Set<Int64> = []

    @State private var generatingLink = false
    @State private var isSharePresented = false
    @State private var shareItems: [Any] = []

    private var friendIDs: Set<Int64> {
        Set(vm.friends.map { $0.friendId })
    }
    private var incomingFromIDs: Set<Int64> {
        Set(received.map { $0.requesterId })
    }
    private var outgoingToIDs: Set<Int64> {
        Set(sent.map { $0.recipientId })
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                
                TextField("Search by username", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(.thinMaterial))
                    .padding(.horizontal)
                    .onChange(of: query) { newValue in
                        
                        searchTask?.cancel()
                        guard !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                            results = []
                            return
                        }
                        searchTask = Task {
                            searching = true
                            defer { searching = false }
                            
                            try? await Task.sleep(nanoseconds: 300_000_000)
                            if Task.isCancelled { return }
                            do {
                                let users = try await UserService.shared.searchUsers(query: newValue, limit: 20)
                                let me = selfUsername?.lowercased()
                                results = users.filter { u in
                                    guard let me = me else { return true }
                                    return u.username.lowercased() != me
                                }
                            } catch {
                                sendError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                            }
                        }
                    }

                if searching { ProgressView().padding(.top, 4) }
                if let sendError {
                    Text(sendError).foregroundStyle(.red).font(.footnote).padding(.horizontal)
                }

                Group {
                    if !query.isEmpty {
                        
                        if results.isEmpty && !searching {
                            ContentUnavailableView(
                                "No matches",
                                systemImage: "magnifyingglass",
                                description: Text("Try a different username")
                            )
                        } else {
                            List(results, id: \.id) { user in
                                let isFriend = friendIDs.contains(user.id)
                                let hasIncoming = incomingFromIDs.contains(user.id)   
                                let hasOutgoing = outgoingToIDs.contains(user.id)     
                                let isSending = sendingIDs.contains(user.id)
                                let isAdded = addedUserIDs.contains(user.id)

                                HStack(spacing: 12) {
                                    Circle()
                                        .frame(width: 32, height: 32)
                                        .opacity(0.1)
                                        .overlay(Text(String(user.username.prefix(1))).font(.subheadline))

                                    Text(user.username).bold()

                                    Spacer()

                                    Button {
                                        
                                        if isFriend {
                                            sendError = "\(user.username) is already your friend."
                                            return
                                        }
                                        if hasIncoming {
                                            sendError = "\(user.username) has already sent you a request. Please accept it in Profile."
                                            return
                                        }
                                        if hasOutgoing || isAdded {
                                            sendError = "You have already sent a friend request to \(user.username)."
                                            return
                                        }

                                        Task {
                                            sendingIDs.insert(user.id)
                                            defer { sendingIDs.remove(user.id) }
                                            do {
                                                try await FriendService.shared.sendRequest(to: user.id)
                                                withAnimation { addedUserIDs.insert(user.id) }
                                                await loadRequests()
                                            } catch {
                                                sendError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                                            }
                                        }
                                    } label: {
                                        if isFriend {
                                            Image(systemName: "person.crop.circle.badge.checkmark").imageScale(.large)
                                        } else if hasIncoming {
                                            Image(systemName: "arrow.down.circle").imageScale(.large)
                                        } else if hasOutgoing || isAdded {
                                            Image(systemName: "checkmark.circle.fill").imageScale(.large)
                                        } else if isSending {
                                            ProgressView().controlSize(.small)
                                        } else {
                                            Image(systemName: "plus.circle.fill").imageScale(.large)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(isFriend || hasIncoming || hasOutgoing || isAdded || isSending)
                                    .accessibilityLabel(accessibilityLabelFor(user: user.username,
                                                                              friend: isFriend,
                                                                              incoming: hasIncoming,
                                                                              outgoing: hasOutgoing || isAdded))
                                }
                            }
                            .listStyle(.insetGrouped)
                        }
                    } else {
                        
                        if vm.loading {
                            ProgressView().controlSize(.large)
                        } else if let err = vm.error {
                            ContentUnavailableView(
                                "Error",
                                systemImage: "exclamationmark.triangle",
                                description: Text(err)
                            )
                        } else if vm.friends.isEmpty {
                            ContentUnavailableView(
                                "No friends yet",
                                systemImage: "person.2.slash",
                                description: Text("Search above to add friends")
                            )
                        } else {
                            List(vm.friends) { f in
                                HStack {
                                    Circle()
                                        .frame(width: 36, height: 36)
                                        .opacity(0.1)
                                        .overlay(Text(String(f.friendUsername.prefix(1))).font(.headline))
                                    
                                    Text(f.friendUsername).font(.body).bold()
                                }
                            }
                            .listStyle(.insetGrouped)
                        }
                    }
                }
            }
            .navigationTitle("Friends")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task {
                            generatingLink = true
                            defer { generatingLink = false }
                            do {
                                let dto = try await FriendService.shared.createVanityInviteLink()
                                shareItems = [URL(string: dto.url) ?? dto.url]
                                isSharePresented = true
                            } catch {
                                sendError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                            }
                        }
                    } label: {
                        if generatingLink {
                            ProgressView()
                        } else {
                            Image(systemName: "link")
                        }
                    }
                    .disabled(generatingLink)
                    .accessibilityLabel("Share invite link")
                }
            }
        }
        .sheet(isPresented: $isSharePresented) {
            ShareSheet(activityItems: shareItems)
        }
        .task {
            await vm.reload()
            selfUsername = currentUsernameFromToken()
            await loadRequests()
        }
        .onDisappear { searchTask?.cancel() }
    }

    private func loadRequests() async {
        do {
            async let rcv = FriendService.shared.listReceivedRequests()
            async let snt = FriendService.shared.listSentRequests()
            (received, sent) = try await (rcv, snt)
        } catch {
            
            sendError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    private func accessibilityLabelFor(user: String, friend: Bool, incoming: Bool, outgoing: Bool) -> String {
        if friend { return "\(user) is already your friend" }
        if incoming { return "\(user) has already sent you a request" }
        if outgoing { return "Request already sent to \(user)" }
        return "Add \(user)"
    }
}



private extension Data {
    init?(base64URLEncoded input: String) {
        var s = input.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let pad = 4 - (s.count % 4)
        if pad < 4 { s += String(repeating: "=", count: pad) }
        self.init(base64Encoded: s)
    }
}


private func currentUsernameFromToken() -> String? {
    guard let token = try? Keychain.get("jwtToken") else { return nil }
    let parts = token.split(separator: ".")
    guard parts.count >= 2,
          let payloadData = Data(base64URLEncoded: String(parts[1])),
          let obj = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any]
    else { return nil }
    return (obj["username"] as? String) ?? (obj["sub"] as? String)
}



