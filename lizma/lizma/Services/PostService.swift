import Foundation

final class PostService {
    static let shared = PostService()

    private init() {}

    func getFeed(page: Int = 0, size: Int = 20) async throws -> [PostResponse] {
        return try await APIClient.shared.send([PostResponse].self, path: "/posts/feed?page=\(page)&size=\(size)")
    }

    func createPost(_ req: PostRequest) async throws -> PostResponse {
        return try await APIClient.shared.send(PostResponse.self, path: "/posts", method: "POST", body: req)
    }

    func reactToPost(postId: Int, type: String) async throws {
        struct EmptyBody: Encodable {}
        let _: EmptyResponse = try await APIClient.shared.send(EmptyResponse.self, path: "/posts/\(postId)/react", method: "POST", query: [URLQueryItem(name: "type", value: type)], body: EmptyBody())
    }

    func addComment(postId: Int, req: CommentRequest) async throws -> CommentResponse {
        return try await APIClient.shared.send(CommentResponse.self, path: "/posts/\(postId)/comments", method: "POST", body: req)
    }

    func getComments(postId: Int) async throws -> [CommentResponse] {
        return try await APIClient.shared.send([CommentResponse].self, path: "/posts/\(postId)/comments")
    }

    func getReactions(postId: Int) async throws -> [ReactionDetailResponse] {
        return try await APIClient.shared.send([ReactionDetailResponse].self, path: "/posts/\(postId)/reactions")
    }

    func toggleLikeComment(postId: Int, commentId: Int) async throws {
        struct EmptyBody: Encodable {}
        let _: EmptyResponse = try await APIClient.shared.send(EmptyResponse.self, path: "/posts/\(postId)/comments/\(commentId)/like", method: "POST", body: EmptyBody())
    }
}
