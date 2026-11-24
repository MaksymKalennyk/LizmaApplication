import Foundation
import UIKit

/// Profiles: anyone's page and posts, and editing your own name and photo.
final class ProfileService {
    static let shared = ProfileService()
    private init() {}

    func profile(username: String) async throws -> ProfileResponse {
        try await APIClient.shared.send(ProfileResponse.self, path: "/user/profiles/\(segment(username))")
    }

    /// Newest first; `before` continues after that post.
    func posts(username: String, before: Int? = nil, size: Int = 30) async throws -> [PostResponse] {
        var query = [URLQueryItem(name: "size", value: String(size))]
        if let before { query.append(URLQueryItem(name: "before", value: String(before))) }
        return try await APIClient.shared.send([PostResponse].self, path: "/user/profiles/\(segment(username))/posts",
                                               query: query)
    }

    /// An empty name removes it (the username is shown instead).
    func setDisplayName(_ name: String?) async throws -> ProfileResponse {
        try await APIClient.shared.send(ProfileResponse.self, path: "/user/me/profile", method: "PUT",
                                        body: DisplayNameRequest(displayName: name))
    }

    /// Sends the photo as a JPEG of at most 1024 px, upright; the server keeps a 512 px square of it.
    func setPhoto(_ image: UIImage) async throws -> String {
        guard let data = Self.uploadable(image) else { throw APIError.unknown }
        return try await APIClient.shared.upload(AvatarResponse.self, path: "/user/me/avatar", field: "file",
                                                 fileName: "photo.jpg", mimeType: "image/jpeg", data: data).avatarUrl
    }

    func removePhoto() async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/user/me/avatar", method: "DELETE")
    }

    /// Redrawn, so the rotation from the camera is applied to the pixels (the server ignores metadata).
    static func uploadable(_ image: UIImage, maxSide: CGFloat = 1024) -> Data? {
        let longest = max(image.size.width, image.size.height)
        guard longest > 0 else { return nil }
        let scale = min(1, maxSide / longest)
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let redrawn = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return redrawn.jpegData(compressionQuality: 0.9)
    }

    private func segment(_ username: String) -> String {
        username.addingPercentEncoding(withAllowedCharacters: .urlPathSegmentAllowed) ?? username
    }
}
