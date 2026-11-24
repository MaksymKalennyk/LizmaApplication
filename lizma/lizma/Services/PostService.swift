import Foundation

final class PostService {
    static let shared = PostService()

    private init() {}

    func getFeed(page: Int = 0, size: Int = 20) async throws -> [PostResponse] {
        return try await APIClient.shared.send([PostResponse].self,
                                               path: "/posts/feed",
                                               query: [URLQueryItem(name: "page", value: String(page)),
                                                       URLQueryItem(name: "size", value: String(size))])
    }

    /// One post (yours or a friend's), e.g. the one a notification is about.
    func getPost(postId: Int) async throws -> PostResponse {
        try await APIClient.shared.send(PostResponse.self, path: "/posts/\(postId)")
    }

    func createPost(_ req: PostRequest) async throws -> PostResponse {
        return try await APIClient.shared.send(PostResponse.self, path: "/posts", method: "POST", body: req)
    }

    /// Deletes the user's own post with its comments and reactions.
    func deletePost(postId: Int) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self, path: "/posts/\(postId)", method: "DELETE")
    }

    /// Deletes a comment (a top-level one with its replies): the user's own, or any comment on their post.
    func deleteComment(postId: Int, commentId: Int) async throws {
        _ = try await APIClient.shared.send(MessageResponse.self,
                                            path: "/posts/\(postId)/comments/\(commentId)",
                                            method: "DELETE")
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
