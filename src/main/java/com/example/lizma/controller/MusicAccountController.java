package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.service.MusicAccountService;
import com.example.lizma.service.SpotifyOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/music")
public class MusicAccountController {

    private final MusicAccountService musicAccountService;
    private final SpotifyOAuthService spotifyOAuthService;

    @Value("${apple.devToken:}")
    private String appleDevToken;

    @Operation(summary = "Get connected providers status for current user")
    @GetMapping("/status")
    public MusicAccountService.Status status(@AuthenticationPrincipal Users me) {
        return musicAccountService.getStatus(me.getId());
    }

    @Operation(summary = "Exchange Spotify code (PKCE) and connect account")
    @PostMapping("/spotify/exchange")
    public Map<String, String> spotifyExchange(@Valid @RequestBody SpotifyExchangeRequest req,
                                               @AuthenticationPrincipal Users me) {
        SpotifyOAuthService.ExchangeResult res = spotifyOAuthService.exchangeCode(req.getCode(), req.getCodeVerifier());
        musicAccountService.connectSpotify(me.getId(), res.accessToken(), res.refreshToken(), res.accessTokenExpiresAt());
        return Map.of("message", "Spotify connected");
    }

    @Operation(summary = "Disconnect Spotify: the stored tokens are deleted (posts stay)")
    @DeleteMapping("/spotify")
    public Map<String, String> spotifyDisconnect(@AuthenticationPrincipal Users me) {
        musicAccountService.disconnectSpotify(me.getId());
        return Map.of("message", "Spotify disconnected");
    }

    @Operation(summary = "Get Apple Music developer token (for MusicKit on device)")
    @GetMapping("/apple/dev-token")
    public Map<String, String> appleDevToken() {
        return Map.of("developerToken", appleDevToken);
    }

    @Operation(summary = "Connect Apple Music by saving user token")
    @PostMapping("/apple/connect")
    public Map<String, String> appleConnect(@Valid @RequestBody AppleConnectRequest req, @AuthenticationPrincipal Users me) {
        musicAccountService.connectApple(me.getId(), req.getUserToken(), req.getStorefront());
        return Map.of("message", "Apple Music connected");
    }

    @Operation(summary = "Search Spotify tracks (requires a connected Spotify account)")
    @GetMapping("/search")
    public JsonNode searchSpotify(@RequestParam String query, @AuthenticationPrincipal Users me) {
        return musicAccountService.searchSpotify(me.getId(), query);
    }

    @Data
    public static class SpotifyExchangeRequest {
        @NotBlank(message = "code is required")
        private String code;
        @NotBlank(message = "codeVerifier is required")
        private String codeVerifier;
    }

    @Data
    public static class AppleConnectRequest {
        @NotBlank(message = "userToken is required")
        private String userToken;
        @NotBlank(message = "storefront is required")
        private String storefront;
    }
}
