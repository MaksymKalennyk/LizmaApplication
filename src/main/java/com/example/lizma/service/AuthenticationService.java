package com.example.lizma.service;

import com.example.lizma.exception.UnauthorizedActionException;
import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.JwtAuthenticationResponse;
import com.example.lizma.model.dto.SignInRequest;
import com.example.lizma.model.dto.SignUpRequest;
import com.example.lizma.model.enums.Role;
import com.example.lizma.service.push.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    public static final String SUSPENDED_MESSAGE = "This account has been suspended";

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ContentFilter contentFilter;
    private final RefreshTokenService refreshTokens;
    private final AuthRateLimiter rateLimiter;
    private final NotificationService notifications;

    public JwtAuthenticationResponse signUp(SignUpRequest request, String address) {
        rateLimiter.checkSignUp(address);
        contentFilter.check(request.getUsername());
        var user = Users.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .termsAcceptedAt(Instant.now())
                .build();

        userService.create(user);
        return tokensFor(user);
    }

    /** Wrong passwords (and unknown names) count towards the limits of {@link AuthRateLimiter}. */
    public JwtAuthenticationResponse signIn(SignInRequest request, String address) {
        String username = request.getUsername();
        rateLimiter.checkSignIn(username, address);
        if (!userService.usernameExists(username)) {
            rateLimiter.signInFailed(username, address);
            throw new UserNotFoundException("User not found, please sign up");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (DisabledException ex) {
            throw new UnauthorizedActionException(SUSPENDED_MESSAGE);
        } catch (BadCredentialsException ex) {
            rateLimiter.signInFailed(username, address);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password");
        }
        rateLimiter.signInSucceeded(username);
        return tokensFor(userService.getByUsername(username));
    }

    /** A new pair for a valid refresh token; the old refresh token stops working. */
    public JwtAuthenticationResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again"));
        return new JwtAuthenticationResponse(jwtService.generateToken(rotation.user()), rotation.refreshToken(),
                jwtService.ttlSeconds());
    }

    /**
     * Ends the session of this refresh token (its access token runs out within minutes) and stops push
     * notifications to the phone signing out. The refresh token proves whose phone it is.
     */
    public void signOut(String refreshToken, String deviceToken) {
        refreshTokens.revokeSession(refreshToken).ifPresent(userId -> {
            if (deviceToken != null && !deviceToken.isBlank()) {
                notifications.unregisterDevice(userId, deviceToken);
            }
        });
    }

    private JwtAuthenticationResponse tokensFor(Users user) {
        return new JwtAuthenticationResponse(jwtService.generateToken(user), refreshTokens.issue(user), jwtService.ttlSeconds());
    }
}
