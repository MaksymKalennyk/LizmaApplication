package com.example.lizma.controller;

import com.example.lizma.service.MusicAccountService;
import com.example.lizma.service.SpotifyOAuthService;
import com.example.lizma.service.UserService;
import io.swagger.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/music")
public class MusicAccountController {

    private final UserService userService;
    private final MusicAccountService musicAccountService;
    private final SpotifyOAuthService spotifyOAuthService;

    @Value("${apple.devToken:}")
    private String appleDevToken;

    @Operation(summary = "Get connected providers status for current user")
    @GetMapping("/status")
    public MusicAccountService.Status status(Principal principal) {
        return musicAccountService.getStatus(userService.getCurrentUserId(principal));
    }

    @Operation(summary = "Exchange Spotify code (PKCE) and connect account")
    @PostMapping("/spotify/exchange")
    public ResponseEntity<Map<String,String>> spotifyExchange(@RequestBody SpotifyExchangeRequest req,
                                                              Principal principal) {
        Long me = userService.getCurrentUserId(principal);
        SpotifyOAuthService.ExchangeResult res = spotifyOAuthService.exchangeCode(req.getCode(), req.getCodeVerifier());
        musicAccountService.connectSpotify(me, res.accessToken(), res.refreshToken(), res.accessTokenExpiresAt());
        return ResponseEntity.ok(Map.of("message","Spotify connected"));
    }

    @Operation(summary = "Get Apple Music developer token (for MusicKit on device)")
    @GetMapping("/apple/dev-token")
    public Map<String,String> appleDevToken() {
        return Map.of("developerToken", appleDevToken);
    }

    @Operation(summary = "Get Spotify authorization URL for client login")
    @GetMapping("/spotify/auth-url")
    public Map<String,String> spotifyAuthUrl() {
        return Map.of("authUrl", spotifyOAuthService.getAuthorizeUrl());
    }

    @Data public static class AppleConnectRequest {

        @NotBlank private String userToken;
        @NotBlank private String storefront;
    }

    @Operation(summary = "Connect Apple Music by saving user token")
    @PostMapping("/apple/connect")
    public ResponseEntity<Map<String,String>> appleConnect(@RequestBody AppleConnectRequest req, Principal principal) {
        Long me = userService.getCurrentUserId(principal);
        musicAccountService.connectApple(me, req.getUserToken(), req.getStorefront());
        return ResponseEntity.ok(Map.of("message","Apple Music connected"));
    }

    @Data
    public static class SpotifyExchangeRequest {
        @NotBlank private String code;
        @NotBlank private String codeVerifier;
    }

    @Operation(summary = "Search Spotify tracks")
    @GetMapping("/search")
    public ResponseEntity<com.fasterxml.jackson.databind.JsonNode> searchSpotify(
            @RequestParam String query,
            Principal principal) {
        Long me = userService.getCurrentUserId(principal);
        return ResponseEntity.ok(musicAccountService.searchSpotify(me, query));
    }
}