package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.ExternalServiceException;
import com.example.lizma.exception.InvalidActionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyOAuthService {

    private static final URI TOKEN_URI = URI.create("https://accounts.spotify.com/api/token");
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String RECONNECT_MESSAGE = "Spotify session expired, please reconnect Spotify";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${spotify.clientId}")
    private String clientId;
    @Value("${spotify.redirectUri}")
    private String redirectUri;

    public record ExchangeResult(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}

    public String getAuthorizeUrl() {
        // The client builds its own PKCE URL; this one is a plain helper without code_challenge.
        return "https://accounts.spotify.com/authorize?" +
                "client_id=" + enc(clientId) +
                "&response_type=code" +
                "&redirect_uri=" + enc(redirectUri) +
                "&scope=" + enc("user-read-private user-read-email");
    }

    /** Exchanges a PKCE authorization code for access and refresh tokens. */
    public ExchangeResult exchangeCode(String code, String codeVerifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", clientId);
        form.put("code_verifier", codeVerifier);

        HttpResponse<String> response = postForm(form);
        if (response.statusCode() == 400) {
            log.info("Spotify rejected the authorization code: {}", response.body());
            throw new InvalidActionException("Spotify authorization failed, please try connecting again");
        }
        JsonNode json = parseSuccessful(response, "token exchange");
        String access = json.path("access_token").asText(null);
        String refresh = json.path("refresh_token").asText(null);
        if (isBlank(access) || isBlank(refresh)) {
            throw new ExternalServiceException("Spotify returned an incomplete response");
        }
        return new ExchangeResult(access, refresh, expiresAt(json));
    }

    public ExchangeResult refreshAccessToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);

        HttpResponse<String> response = postForm(form);
        if (response.statusCode() == 400) { // invalid_grant: the refresh token was revoked or already used
            log.info("Spotify refused to refresh the token: {}", response.body());
            throw new ConflictException(RECONNECT_MESSAGE);
        }
        JsonNode json = parseSuccessful(response, "token refresh");
        String access = json.path("access_token").asText(null);
        if (isBlank(access)) {
            throw new ExternalServiceException("Spotify returned an incomplete response");
        }
        // Spotify may rotate the refresh token; keep the old one if it didn't.
        String newRefresh = json.path("refresh_token").asText(refreshToken);
        return new ExchangeResult(access, newRefresh, expiresAt(json));
    }

    public JsonNode searchTracks(String query, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL + "?type=track&limit=20&q=" + enc(query)))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 401) {
            throw new ConflictException(RECONNECT_MESSAGE);
        }
        return parseSuccessful(response, "search").path("tracks").path("items");
    }

    private HttpResponse<String> postForm(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> enc(entry.getKey()) + "=" + enc(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Spotify request to {} failed: {}", request.uri().getPath(), e.toString());
            throw new ExternalServiceException("Spotify is unavailable, please try again later");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Spotify request was interrupted");
        }
    }

    private JsonNode parseSuccessful(HttpResponse<String> response, String operation) {
        if (response.statusCode() / 100 != 2) {
            log.warn("Spotify {} failed: HTTP {} {}", operation, response.statusCode(), response.body());
            throw new ExternalServiceException(response.statusCode() == 429
                    ? "Too many requests to Spotify, please try again later"
                    : "Spotify request failed, please try again later");
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException("Spotify returned an invalid response");
        }
    }

    private static Instant expiresAt(JsonNode json) {
        return Instant.now().plusSeconds(json.path("expires_in").asInt(3600));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
