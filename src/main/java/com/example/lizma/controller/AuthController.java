package com.example.lizma.controller;

import com.example.lizma.model.dto.JwtAuthenticationResponse;
import com.example.lizma.model.dto.LogoutRequest;
import com.example.lizma.model.dto.RefreshRequest;
import com.example.lizma.model.dto.SignInRequest;
import com.example.lizma.model.dto.SignUpRequest;
import com.example.lizma.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationService authenticationService;

    @Operation(summary = "User registration: returns an access token and a refresh token")
    @PostMapping("/sign-up")
    public JwtAuthenticationResponse signUp(@RequestBody @Valid SignUpRequest request, HttpServletRequest http) {
        return authenticationService.signUp(request, http.getRemoteAddr());
    }

    @Operation(summary = "Sign in: returns an access token and a refresh token (too many wrong passwords: 429)")
    @PostMapping("/sign-in")
    public JwtAuthenticationResponse signIn(@RequestBody @Valid SignInRequest request, HttpServletRequest http) {
        return authenticationService.signIn(request, http.getRemoteAddr());
    }

    @Operation(summary = "Trade a refresh token for a new access token and refresh token (the old one stops working)")
    @PostMapping("/refresh")
    public JwtAuthenticationResponse refresh(@RequestBody @Valid RefreshRequest request) {
        return authenticationService.refresh(request.refreshToken());
    }

    @Operation(summary = "Sign out: the refresh token's session ends, and the phone's push notifications (deviceToken) stop")
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody @Valid LogoutRequest request) {
        authenticationService.signOut(request.refreshToken(), request.deviceToken());
        return Map.of("message", "Signed out");
    }
}
