package com.example.lizma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SpotifyOAuthService {

    @Value("${spotify.clientId}")
    private String clientId;
    @Value("${spotify.redirectUri}")
    private String redirectUri;

    private final ObjectMapper om = new ObjectMapper();

    public record ExchangeResult(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}

    public String getAuthorizeUrl() {
        // Use a generic code challenge for simplicity on client side or redirect logic
        // It's recommended that the client handles PKCE itself, but providing standard URL helps.
        return "https://accounts.spotify.com/authorize?" +
                "client_id=" + enc(clientId) +
                "&response_type=code" +
                "&redirect_uri=" + enc(redirectUri) +
                "&scope=" + enc("user-read-private user-read-email");
    }

    public ExchangeResult exchangeCode(String code, String codeVerifier) {
        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&client_id=" + enc(clientId)
                    + "&code_verifier=" + enc(codeVerifier);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Spotify token exchange failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode json = om.readTree(resp.body());
            String refresh = json.path("refresh_token").asText(null);
            String access = json.path("access_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(3600);
            Instant expAt = Instant.now().plusSeconds(expiresIn);
            if (refresh == null || refresh.isBlank() || access == null || access.isBlank()) {
                throw new RuntimeException("Missing tokens in Spotify response");
            }
            return new ExchangeResult(access, refresh, expAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ExchangeResult refreshAccessToken(String refreshToken) {
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + enc(refreshToken)
                    + "&client_id=" + enc(clientId);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Spotify token refresh failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode json = om.readTree(resp.body());
            String newRefresh = json.path("refresh_token").asText(refreshToken); // sometimes Spotify returns a new one
            String access = json.path("access_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(3600);
            Instant expAt = Instant.now().plusSeconds(expiresIn);
            
            return new ExchangeResult(access, newRefresh, expAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh: " + e.getMessage(), e);
        }
    }

    public JsonNode searchTracks(String query, String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/search?type=track&q=" + enc(query) + "&limit=20"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Spotify search failed: " + resp.statusCode() + " " + resp.body());
            }
            return om.readTree(resp.body()).path("tracks").path("items");
        } catch (Exception e) {
            throw new RuntimeException("Failed to search tracks: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}