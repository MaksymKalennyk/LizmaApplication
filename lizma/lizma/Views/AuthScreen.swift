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

    /// Sign-up requires agreeing to the Terms of Use and Privacy Policy.
    @State private var acceptedTerms = false
    @State private var safariLink: SafariLink? = nil
    @ObservedObject private var legal = LegalInfoStore.shared

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
                        .onChange(of: auth.username) { inputChanged() }
                        .onChange(of: usernameFocused) { _, focused in
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
                        .onChange(of: auth.password) { inputChanged() }
                        .onChange(of: passwordFocused) { _, focused in
                            if focused { passwordTouched = true }
                        }

                    if shouldShowPasswordErrorText, let e = passwordError {
                        Text(e).font(.caption).foregroundStyle(.red)
                    }
                }
            }
            .padding(.horizontal)

            if !isSignIn {
                termsAgreement
                    .padding(.horizontal)
            }

            if let err = auth.error {
                Text(err).foregroundStyle(.red).multilineTextAlignment(.center).padding(.horizontal)
            } else if let notice = auth.notice {
                Text(notice).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal)
            }

            Button(action: {
                didSubmit = true
                validate()
                guard usernameError == nil, passwordError == nil else { return }
                guard isSignIn || acceptedTerms else { return }
                Task { await (isSignIn ? auth.signIn() : auth.signUp(acceptTerms: acceptedTerms)) }
            }) {
                HStack {
                    if auth.isLoading { ProgressView().tint(.white) }
                    Text(isSignIn ? "Sign in" : "Create account")
                        .font(.headline)
                }
                .frame(maxWidth: .infinity)
                .padding()
                // The custom fill ignores `.disabled`, so draw the unavailable state explicitly.
                .background(Capsule().fill(submitUnavailable ? AnyShapeStyle(Color(.systemGray4)) : AnyShapeStyle(.tint)))
                .foregroundStyle(submitUnavailable ? AnyShapeStyle(Color(.secondaryLabel)) : AnyShapeStyle(.white))
                .padding(.horizontal)
                .animation(.easeInOut(duration: 0.15), value: submitUnavailable)
            }
            .disabled(auth.isLoading || submitUnavailable)

            Button(isSignIn ? "No account? Sign up" : "Already have an account? Sign in") {
                // A server error belongs to the other mode's attempt.
                auth.error = nil
                auth.notice = nil
                withAnimation { isSignIn.toggle() }
                validate()
            }
            .padding(.top, 4)

            Spacer()
            Text("Liza's idea, Max's implementation")
                .font(.footnote).foregroundStyle(.secondary).padding(.bottom)
        }
        .onAppear { validate() }
        .task { await legal.loadIfNeeded() }
        .sheet(item: $safariLink) { link in
            SafariView(url: link.url)
                .ignoresSafeArea()
        }
        .background(
            // System colors keep the soft gradient in light mode and stay readable in dark mode.
            LinearGradient(colors: [Color(.secondarySystemBackground), Color(.systemBackground)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            .ignoresSafeArea()
        )
    }

    /// Required checkbox plus tappable links to the legal pages (opened in-app).
    private var termsAgreement: some View {
        HStack(alignment: .top, spacing: 10) {
            Button {
                acceptedTerms.toggle()
            } label: {
                Image(systemName: acceptedTerms ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(acceptedTerms ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("I agree to the Terms of Use and Privacy Policy")
            .accessibilityValue(acceptedTerms ? "Checked" : "Not checked")
            .accessibilityAddTraits(.isToggle)

            // The links use a private scheme that is handled right here, never by the system.
            Text("I agree to the [Terms of Use](lizma-legal://terms) and the [Privacy Policy](lizma-legal://privacy).")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .environment(\.openURL, OpenURLAction { url in openLegalLink(url) })
        }
    }

    private func openLegalLink(_ url: URL) -> OpenURLAction.Result {
        let destination: URL?
        switch url.host {
        case "terms": destination = legal.termsURL
        case "privacy": destination = legal.privacyURL
        default: return .systemAction
        }
        if let destination {
            safariLink = SafariLink(url: destination)
        }
        return .handled
    }

    /// Something is missing (invalid field, terms not accepted). A request in flight also disables
    /// the button but keeps the tint, so the spinner stays visible.
    private var submitUnavailable: Bool {
        usernameError != nil || passwordError != nil || (!isSignIn && !acceptedTerms)
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

    /// The server's answer was about the previous input, so it goes away once that input is edited.
    private func inputChanged() {
        // Signing in clears the password; without this the screen would leave with a red
        // "Password cannot be blank" on it.
        guard !auth.isSignedIn else { return }
        if !auth.isLoading { auth.error = nil }
        validate()
    }

    private func validate() {
        let u = auth.username.trimmingCharacters(in: .whitespacesAndNewlines)
        if u.isEmpty {
            usernameError = String(localized: "Username cannot be blank", bundle: .app)
        } else if !UsernameValidator.lengthRange.contains(u.count) {
            usernameError = UsernameValidator.lengthMessage
        } else if !isSignIn && !UsernameValidator.hasOnlyAllowedCharacters(u) {
            // Only enforced for new accounts: older usernames may predate the rule and must still sign in.
            usernameError = UsernameValidator.charactersMessage
        } else {
            usernameError = nil
        }

        let p = auth.password
        if p.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            passwordError = String(localized: "Password cannot be blank", bundle: .app)
        } else if p.count < passwordMin || p.count > passwordMax {
            passwordError = String(localized: "Password length must be between \(passwordMin) and \(passwordMax) characters", bundle: .app)
        } else {
            passwordError = nil
        }
    }
}
