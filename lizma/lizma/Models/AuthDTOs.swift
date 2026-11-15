import Foundation

struct SignInRequest: Encodable { let username: String; let password: String }
struct SignUpRequest: Encodable {
    let username: String
    let password: String
    /// Must be true: the user agreed to the Terms of Use and Privacy Policy.
    let acceptTerms: Bool
}
