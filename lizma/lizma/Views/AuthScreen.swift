import SwiftUI

struct AuthScreen: View {
    @EnvironmentObject private var auth: AuthViewModel
    @State private var isSignIn = true

    @State private var usernameError: String? = nil
    @State private var passwordError: String? = nil

    @FocusState private var usernameFocused: Bool
    @FocusState private var passwordFocused: Bool
    @State private var usernameTouched = false
    @State private var passwordTouched = false
    @State private var didSubmit = false

    private let usernameMin = 5
    private let usernameMax = 50
    private let passwordMin = 8
    private let passwordMax = 255

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            VStack(spacing: 8) {
                Text("Lizma")
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                Text(isSignIn ? "Sign in" : "Sign up")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 8) {
                
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Username", text: $auth.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($usernameFocused)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(.thinMaterial)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(shouldShowUsernameErrorBorder ? Color.red.opacity(0.6) : .clear, lineWidth: 1)
                                )
                        )
                        .onChange(of: auth.username) { _ in validate() }
                        .onChange(of: usernameFocused) { focused in
                            if focused { usernameTouched = true }
                        }

                    if shouldShowUsernameErrorText, let e = usernameError {
                        Text(e).font(.caption).foregroundStyle(.red)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    SecureField("Password", text: $auth.password)
                        .focused($passwordFocused)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(.thinMaterial)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(shouldShowPasswordErrorBorder ? Color.red.opacity(0.6) : .clear, lineWidth: 1)
                                )
                        )
                        .onChange(of: auth.password) { _ in validate() }
                        .onChange(of: passwordFocused) { focused in
                            if focused { passwordTouched = true }
                        }

                    if shouldShowPasswordErrorText, let e = passwordError {
                        Text(e).font(.caption).foregroundStyle(.red)
                    }
                }
            }
            .padding(.horizontal)

            if let err = auth.error {
                Text(err).foregroundStyle(.red).multilineTextAlignment(.center).padding(.horizontal)
            }

            Button(action: {
                didSubmit = true
                validate()
                guard usernameError == nil, passwordError == nil else { return }
                Task { await (isSignIn ? auth.signIn() : auth.signUp()) }
            }) {
                HStack {
                    if auth.isLoading { ProgressView().tint(.white) }
                    Text(isSignIn ? "Sign in" : "Create account")
                        .font(.headline)
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Capsule().fill(.tint))
                .foregroundStyle(.white)
                .padding(.horizontal)
            }
            .disabled(auth.isLoading || usernameError != nil || passwordError != nil)

            Button(isSignIn ? "No account? Sign up" : "Already have an account? Sign in") {
                withAnimation { isSignIn.toggle() }
            }
            .padding(.top, 4)

            Spacer()
            Text("Liza's idea, Max's implementation")
                .font(.footnote).foregroundStyle(.secondary).padding(.bottom)
        }
        .onAppear { validate() }
        .background(
            LinearGradient(gradient: Gradient(colors: [.init(white: 0.95), .white]),
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            .ignoresSafeArea()
        )
    }

    private var shouldShowUsernameErrorBorder: Bool {
        (usernameTouched || didSubmit) && usernameError != nil
    }
    private var shouldShowPasswordErrorBorder: Bool {
        (passwordTouched || didSubmit) && passwordError != nil
    }
    private var shouldShowUsernameErrorText: Bool {
        (usernameTouched || didSubmit) && usernameError != nil
    }
    private var shouldShowPasswordErrorText: Bool {
        (passwordTouched || didSubmit) && passwordError != nil
    }

    private func validate() {
        let u = auth.username.trimmingCharacters(in: .whitespacesAndNewlines)
        if u.isEmpty {
            usernameError = "Username cannot be blank"
        } else if u.count < usernameMin || u.count > usernameMax {
            usernameError = "Username must be between \(usernameMin) and \(usernameMax) characters"
        } else {
            usernameError = nil
        }

        let p = auth.password
        if p.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            passwordError = "Password cannot be blank"
        } else if p.count < passwordMin || p.count > passwordMax {
            passwordError = "Password length must be between \(passwordMin) and \(passwordMax) characters"
        } else {
            passwordError = nil
        }
    }
}
