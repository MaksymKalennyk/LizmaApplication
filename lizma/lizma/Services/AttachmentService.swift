import Foundation
import UIKit

/// Screenshots for reports and feedback: each is uploaded on its own and the report or feedback carries the ids.
final class AttachmentService {
    static let shared = AttachmentService()
    private init() {}

    private struct UploadResponse: Decodable {
        let id: String
    }

    /// The ids of the uploaded screenshots, in order. Sent as JPEGs of at most 2000 px (what the server keeps).
    func upload(_ images: [UIImage]) async throws -> [String] {
        var ids: [String] = []
        for image in images {
            guard let data = ProfileService.uploadable(image, maxSide: 2000) else { throw APIError.unknown }
            let response = try await APIClient.shared.upload(UploadResponse.self, path: "/user/attachments", method: "POST",
                                                             field: "file", fileName: "screenshot.jpg",
                                                             mimeType: "image/jpeg", data: data)
            ids.append(response.id)
        }
        return ids
    }
}
