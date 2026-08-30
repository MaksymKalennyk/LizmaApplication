import Foundation

struct SignInRequest: Encodable { let username: String; let password: String }
struct SignUpRequest: Encodable { let username: String; let password: String }
