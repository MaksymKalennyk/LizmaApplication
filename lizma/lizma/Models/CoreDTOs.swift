import Foundation

struct AnyEncodable: Encodable {
    private let _encode: (Encoder) throws -> Void
    init(_ wrapped: Encodable) { self._encode = wrapped.encode }
    func encode(to encoder: Encoder) throws { try _encode(encoder) }
}

struct Empty: Decodable {}

/// `{"message": "..."}` replies, e.g. from accepting an invite.
struct MessageResponse: Decodable {
    let message: String
}
