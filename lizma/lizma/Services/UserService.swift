import Foundation

final class UserService {
    static let shared = UserService()
    private init() {}

    private let searchPath = "/user/search"

    func searchUsers(query: String, limit: Int = 20) async throws -> [UserDto] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return try await APIClient.shared.send([UserDto].self,
                                               path: searchPath,
                                               query: [URLQueryItem(name: "query", value: query),
                                                       URLQueryItem(name: "limit", value: String(limit))])
    }
}
