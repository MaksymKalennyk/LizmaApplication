package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.ExternalServiceException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.UnauthorizedActionException;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Talks to Spotify: PKCE code exchange, token refresh and track search.
 * Base URLs are configurable so tests can point them at a fake server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyOAuthService {

    /** Spotify caps search results for development-mode apps at 10 per request (since February 2026). */
    public static final int SEARCH_LIMIT = 10;
    public static final String RECONNECT_MESSAGE = "Spotify session expired, please reconnect Spotify";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${spotify.clientId}")
    private String clientId;
    @Value("${spotify.redirectUri}")
    private String redirectUri;
    @Value("${spotify.accountsBaseUrl:https://accounts.spotify.com}")
    private String accountsBaseUrl;
    @Value("${spotify.apiBaseUrl:https://api.spotify.com}")
    private String apiBaseUrl;

    public record ExchangeResult(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}

    /** Spotify rejected the access token (it expired early or the user revoked access). Refreshing may help. */
    public static class AccessTokenRejectedException extends ConflictException {
        public AccessTokenRejectedException() {
            super(RECONNECT_MESSAGE);
        }
    }

    /** The refresh token no longer works: the user has to connect Spotify again. */
    public static class ReconnectRequiredException extends ConflictException {
        public ReconnectRequiredException() {
            super(RECONNECT_MESSAGE);
        }
    }

    /**
     * Exchanges a PKCE authorization code for tokens, then checks that this Spotify account may use the app:
     * a development-mode app only works for accounts allowlisted in the Spotify Developer Dashboard.
     */
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

        verifyAccountAccess(access);
        return new ExchangeResult(access, refresh, expiresAt(json));
    }

    public ExchangeResult refreshAccessToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);

        HttpResponse<String> response = postForm(form);
        if (response.statusCode() == 400) { // invalid_grant: access was revoked or the token was already used
            log.info("Spotify refused to refresh the token: {}", response.body());
            throw new ReconnectRequiredException();
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
        URI uri = URI.create(apiBaseUrl + "/v1/search?type=track&limit=" + SEARCH_LIMIT + "&q=" + enc(query));
        HttpResponse<String> response = send(authorizedGet(uri, accessToken));
        rejectAccessProblems(response);
        return parseSuccessful(response, "search").path("tracks").path("items");
    }

    /** GET /v1/me works for any valid token, so a 403 here means the account itself isn't allowed. */
    private void verifyAccountAccess(String accessToken) {
        HttpResponse<String> response = send(authorizedGet(URI.create(apiBaseUrl + "/v1/me"), accessToken));
        rejectAccessProblems(response);
        parseSuccessful(response, "account check");
    }

    private void rejectAccessProblems(HttpResponse<String> response) {
        if (response.statusCode() == 401) {
            throw new AccessTokenRejectedException();
        }
        if (response.statusCode() == 403) {
            log.info("Spotify denied access: {}", response.body());
            throw new UnauthorizedActionException(forbiddenMessage(response.body()));
        }
    }

    /** Development-mode apps: only allowlisted accounts work, and the app owner needs Premium. */
    private static String forbiddenMessage(String body) {
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (text.contains("premium")) {
            return "Spotify blocked the request: the owner of the Lizma Spotify app needs an active Premium subscription";
        }
        if (text.contains("registered") || text.contains("user management") || text.contains("dashboard")) {
            return "This Spotify account isn't on Lizma's tester list yet. "
                    + "Ask the app owner to add it in the Spotify Developer Dashboard (User Management)";
        }
        return "Spotify denied access for this account";
    }

    private HttpRequest authorizedGet(URI uri, String accessToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
    }

    private HttpResponse<String> postForm(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> enc(entry.getKey()) + "=" + enc(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(accountsBaseUrl + "/api/token"))
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
