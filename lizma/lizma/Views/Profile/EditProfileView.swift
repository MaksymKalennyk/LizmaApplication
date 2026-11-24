import SwiftUI
import PhotosUI

/// Your photo and display name. The photo is saved as soon as it is picked; the name with "Save".
struct EditProfileView: View {
    let profile: ProfileResponse
    let onSaved: (ProfileResponse) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var displayName: String
    @State private var avatarUrl: String?
    @State private var pickedItem: PhotosPickerItem?
    @State private var isUploading = false
    @State private var isSaving = false
    @State private var errorMessage: String?

    private static let maxNameLength = 50

    init(profile: ProfileResponse, onSaved: @escaping (ProfileResponse) -> Void) {
        self.profile = profile
        self.onSaved = onSaved
        _displayName = State(initialValue: profile.displayName ?? "")
        _avatarUrl = State(initialValue: profile.avatarUrl)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 14) {
                        ZStack {
                            AvatarView(username: profile.username, imageURL: avatarUrl, size: 104)
                            if isUploading {
                                Circle()
                                    .fill(.black.opacity(0.4))
                                    .frame(width: 104, height: 104)
                                ProgressView().tint(.white)
                            }
                        }
                        HStack(spacing: 12) {
                            PhotosPicker(selection: $pickedItem, matching: .images, photoLibrary: .shared()) {
                                Text(avatarUrl == nil ? "Add photo" : "Change photo")
                            }
                            .buttonStyle(.bordered)
                            if avatarUrl != nil {
                                Button("Remove", role: .destructive) {
                                    Task { await removePhoto() }
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .disabled(isUploading)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .listRowBackground(Color.clear)
                }

                Section {
                    TextField("Name", text: $displayName)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                } header: {
                    Text("Name")
                } footer: {
                    Text("Shown instead of @\(profile.username), which stays your username. Leave it empty to show the username.")
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Your profile") // short: Cancel and Save sit on both sides
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await saveName() }
                    } label: {
                        if isSaving { ProgressView() } else { Text("Save") }
                    }
                    .disabled(isSaving || isUploading || trimmedName == (profile.displayName ?? ""))
                }
            }
            .onChange(of: displayName) { _, newValue in
                if newValue.count > Self.maxNameLength {
                    displayName = String(newValue.prefix(Self.maxNameLength))
                }
            }
            .onChange(of: pickedItem) { _, item in
                guard let item else { return }
                Task { await upload(item) }
            }
        }
    }

    private var trimmedName: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func upload(_ item: PhotosPickerItem) async {
        isUploading = true
        errorMessage = nil
        defer {
            isUploading = false
            pickedItem = nil
        }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
                errorMessage = String(localized: "This photo can't be used. Try another one.", bundle: .app)
                return
            }
            avatarUrl = try await ProfileService.shared.setPhoto(image)
            await refreshEverywhere()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }

    private func removePhoto() async {
        isUploading = true
        errorMessage = nil
        defer { isUploading = false }
        do {
            try await ProfileService.shared.removePhoto()
            avatarUrl = nil
            await refreshEverywhere()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }

    private func saveName() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            let updated = try await ProfileService.shared.setDisplayName(trimmedName.isEmpty ? nil : trimmedName)
            onSaved(updated)
            await CurrentUserStore.shared.refresh()
            dismiss()
        } catch {
            guard !error.isCancellation else { return }
            errorMessage = error.userMessage
        }
    }

    /// The photo is saved right away: the profile page and the Profile tab show it at once.
    private func refreshEverywhere() async {
        if let updated = try? await ProfileService.shared.profile(username: profile.username) {
            onSaved(updated)
        }
        await CurrentUserStore.shared.refresh()
    }
}
