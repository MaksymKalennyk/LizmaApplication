import SwiftUI
import UserNotifications

/// Profile → Notifications: whether the phone lets Lizma notify at all, and what the user wants to hear about.
struct NotificationSettingsView: View {
    @ObservedObject private var push = PushManager.shared
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @State private var settings: NotificationSettings?
    @State private var loadError: String?
    @State private var saveError: String?

    var body: some View {
        Form {
            permissionSection

            if let settings {
                Section {
                    Toggle("Friend requests", isOn: binding(\.friendRequests, in: settings))
                    Toggle("Reactions", isOn: binding(\.reactions, in: settings))
                    Toggle("Comments and replies", isOn: binding(\.comments, in: settings))
                } footer: {
                    Text("New friend requests and new friends, the first reaction of each friend to your post, comments on your posts and replies to you.")
                }

                Section {
                    Toggle("Daily reminder", isOn: binding(\.dailyReminder, in: settings))
                } footer: {
                    Text("Every day at \(Self.reminderTime), if you haven't shared a song yet that day.")
                }
            } else if let loadError {
                Section {
                    Text(loadError)
                        .foregroundStyle(.secondary)
                    Button("Try again") {
                        Task { await load() }
                    }
                }
            } else {
                Section {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await push.refreshAuthorization()
            await load()
        }
        // Back from Settings with notifications switched on or off.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await push.refreshAuthorization() }
            }
        }
        .alert("Couldn't save", isPresented: Binding(get: { saveError != nil }, set: { if !$0 { saveError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(saveError ?? "")
        }
    }

    @ViewBuilder
    private var permissionSection: some View {
        switch push.authorization {
        case .denied:
            Section {
                Label("Notifications are off for Lizma", systemImage: "bell.slash")
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        openURL(url)
                    }
                }
            } footer: {
                Text("Turn them on in Settings to hear about friends, reactions and comments.")
            }
        case .notDetermined:
            Section {
                Button {
                    Task { await push.requestPermission() }
                } label: {
                    Label("Allow notifications", systemImage: "bell.badge")
                }
            } footer: {
                Text("Your phone asks first; you can change your mind in Settings at any time.")
            }
        default:
            EmptyView()
        }
    }

    /// Switches one setting right away and saves it; puts it back if the server didn't take it.
    private func binding(_ keyPath: WritableKeyPath<NotificationSettings, Bool>,
                         in current: NotificationSettings) -> Binding<Bool> {
        Binding(get: { settings?[keyPath: keyPath] ?? current[keyPath: keyPath] },
                set: { isOn in
                    guard var changed = settings else { return }
                    let previous = changed
                    changed[keyPath: keyPath] = isOn
                    settings = changed
                    Task { await save(changed, previous: previous) }
                })
    }

    private func save(_ changed: NotificationSettings, previous: NotificationSettings) async {
        do {
            let saved = try await NotificationSettingsService.shared.update(changed)
            if settings == changed { settings = saved }
        } catch {
            guard !error.isCancellation else { return }
            if settings == changed { settings = previous }
            saveError = error.userMessage
        }
    }

    private func load() async {
        loadError = nil
        do {
            settings = try await NotificationSettingsService.shared.settings()
        } catch {
            guard !error.isCancellation else { return }
            loadError = error.userMessage
        }
    }

    /// 13:00 in Kyiv (when the server sends the reminder), in the phone's own time zone and format.
    static var reminderTime: String {
        var kyiv = Calendar(identifier: .gregorian)
        kyiv.timeZone = TimeZone(identifier: "Europe/Kyiv") ?? .current
        let date = kyiv.date(bySettingHour: 13, minute: 0, second: 0, of: Date()) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }
}

#Preview {
    NavigationStack { NotificationSettingsView() }
}
