import Foundation

final class UserService {
    static let shared = UserService()
    private init() {}

    private let lookupPath = "/user/lookup"
    private let searchPath = "/user/search"

    func findUser(username: String) async throws -> UserDto {
        try await APIClient.shared.send(UserDto.self,
                                        path: lookupPath,
                                        query: [URLQueryItem(name: "username", value: username)])
    }

    func searchUsers(query: String, limit: Int = 20) async throws -> [UserDto] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return try await APIClient.shared.send([UserDto].self,
                                               path: searchPath,
                                               query: [URLQueryItem(name: "query", value: query),
                                                       URLQueryItem(name: "limit", value: String(limit))])
    }
}
