package com.example.lizma;

import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.UserMusicAccountRepository;
import com.example.lizma.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spotify connect, search and token refresh against a fake Spotify server: no network and no real
 * Spotify account needed. Covers the cases that are slow or awkward to reproduce by hand (a token
 * expiring after an hour, access revoked by the user, accounts missing from the tester list).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpotifyIntegrationTest {

    private static final FakeSpotify SPOTIFY = FakeSpotify.start();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String NOT_REGISTERED =
            "{\"error\":{\"status\":403,\"message\":\"Check settings on developer.spotify.com/dashboard, the user may not be registered.\"}}";

    @DynamicPropertySource
    static void spotifyProperties(DynamicPropertyRegistry registry) {
        registry.add("spotify.accountsBaseUrl", SPOTIFY::baseUrl);
        registry.add("spotify.apiBaseUrl", SPOTIFY::baseUrl);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:spotify;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopSpotify() {
        SPOTIFY.stop();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserMusicAccountRepository accountRepository;

    private record User(String name, String token) {
    }

    @BeforeEach
    void resetSpotify() {
        SPOTIFY.reset();
    }

    @Test
    void connectChecksTheAccountAndSearchAsksForTenResults() throws Exception {
        User user = newUser();
        SPOTIFY.respond("POST /api/token", 200, tokens("access-1", "refresh-1"));
        SPOTIFY.respond("GET /v1/me", 200, "{\"id\":\"spotify-user\"}");

        connect(user).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Spotify connected"));
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(true));
        assertThat(SPOTIFY.last("POST /api/token").body())
                .contains("grant_type=authorization_code", "code=the-code", "code_verifier=the-verifier");
        assertThat(SPOTIFY.last("GET /v1/me").authorization()).isEqualTo("Bearer access-1");

        SPOTIFY.respond("GET /v1/search", 200, "{\"tracks\":{\"items\":[{\"id\":\"track-1\",\"name\":\"Song\"}]}}");
        as(user, get("/music/search").param("query", "abba gold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("track-1"));
        FakeSpotify.Recorded search = SPOTIFY.last("GET /v1/search");
        assertThat(search.query()).contains("type=track", "limit=10", "q=abba+gold");
        assertThat(search.authorization()).isEqualTo("Bearer access-1");
    }

    @Test
    void accountMissingFromTheTesterListIsRejectedWhenConnecting() throws Exception {
        User user = newUser();
        SPOTIFY.respond("POST /api/token", 200, tokens("access-1", "refresh-1"));
        SPOTIFY.respond("GET /v1/me", 403, NOT_REGISTERED);

        connect(user).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("tester list")));
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(false));
    }

    @Test
    void invalidAuthorizationCodeIsAClientError() throws Exception {
        User user = newUser();
        SPOTIFY.respond("POST /api/token", 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"}");

        connect(user).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Spotify authorization failed, please try connecting again"));
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(false));
    }

    @Test
    void expiredAccessTokenIsRefreshedAndTheRotatedRefreshTokenSaved() throws Exception {
        User user = connectedUser();
        expireAccessToken(user); // as if an hour has passed
        SPOTIFY.respond("POST /api/token", 200, tokens("access-2", "refresh-2"));
        SPOTIFY.respond("GET /v1/search", 200, "{\"tracks\":{\"items\":[]}}");

        as(user, get("/music/search").param("query", "abba")).andExpect(status().isOk());

        assertThat(SPOTIFY.last("POST /api/token").body()).contains("grant_type=refresh_token", "refresh_token=refresh-1");
        assertThat(SPOTIFY.last("GET /v1/search").authorization()).isEqualTo("Bearer access-2");
        UserMusicAccount account = spotifyAccount(user);
        assertThat(account.getSpotifyAccessToken()).isEqualTo("access-2");
        assertThat(account.getSpotifyRefreshToken()).isEqualTo("refresh-2");
        assertThat(account.getSpotifyAccessTokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void refreshKeepsTheOldRefreshTokenWhenSpotifyDoesNotRotateIt() throws Exception {
        User user = connectedUser();
        expireAccessToken(user);
        SPOTIFY.respond("POST /api/token", 200, "{\"access_token\":\"access-2\",\"expires_in\":3600}");
        SPOTIFY.respond("GET /v1/search", 200, "{\"tracks\":{\"items\":[]}}");

        as(user, get("/music/search").param("query", "abba")).andExpect(status().isOk());

        assertThat(spotifyAccount(user).getSpotifyRefreshToken()).isEqualTo("refresh-1");
    }

    @Test
    void rejectedAccessTokenIsRefreshedOnceAndTheSearchRetried() throws Exception {
        User user = connectedUser();
        SPOTIFY.respond("GET /v1/search", 401, "{\"error\":{\"status\":401,\"message\":\"The access token expired\"}}");
        SPOTIFY.respond("POST /api/token", 200, tokens("access-2", "refresh-1"));
        SPOTIFY.respond("GET /v1/search", 200, "{\"tracks\":{\"items\":[{\"id\":\"track-1\"}]}}");

        as(user, get("/music/search").param("query", "abba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("track-1"));
        assertThat(SPOTIFY.all("GET /v1/search")).extracting(FakeSpotify.Recorded::authorization)
                .containsExactly("Bearer access-1", "Bearer access-2");
    }

    @Test
    void revokedAccessRemovesTheConnectionAndAsksToReconnect() throws Exception {
        User user = connectedUser();
        // The user removed Lizma at spotify.com/account/apps: the access token and the refresh token stop working.
        SPOTIFY.respond("GET /v1/search", 401, "{\"error\":{\"status\":401,\"message\":\"Invalid access token\"}}");
        SPOTIFY.respond("POST /api/token", 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}");

        as(user, get("/music/search").param("query", "abba"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Spotify session expired, please reconnect Spotify"));
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(false));
    }

    @Test
    void searchOutsideTheTesterListAndRateLimitsAreReportedClearly() throws Exception {
        User user = connectedUser();

        SPOTIFY.respond("GET /v1/search", 403, NOT_REGISTERED);
        as(user, get("/music/search").param("query", "abba"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("tester list")));

        SPOTIFY.respond("GET /v1/search", 429, "{\"error\":{\"status\":429,\"message\":\"API rate limit exceeded\"}}");
        as(user, get("/music/search").param("query", "abba"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Too many requests to Spotify, please try again later"));

        // neither problem is the user's token: the connection stays
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(true));
    }

    // ---------------------------------------------------------------- helpers

    private User newUser() throws Exception {
        String name = "spotify_user_" + SEQ.incrementAndGet();
        String body = objectMapper.writeValueAsString(Map.of("username", name, "password", "password123", "acceptTerms", true));
        String response = mvc.perform(post("/auth/sign-up").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new User(name, objectMapper.readTree(response).get("token").asText());
    }

    /** A user whose Spotify is connected with access-1 / refresh-1. */
    private User connectedUser() throws Exception {
        User user = newUser();
        SPOTIFY.respond("POST /api/token", 200, tokens("access-1", "refresh-1"));
        SPOTIFY.respond("GET /v1/me", 200, "{\"id\":\"spotify-user\"}");
        connect(user).andExpect(status().isOk());
        SPOTIFY.reset();
        return user;
    }

    private ResultActions connect(User user) throws Exception {
        return as(user, post("/music/spotify/exchange").contentType(APPLICATION_JSON)
                .content("{\"code\":\"the-code\",\"codeVerifier\":\"the-verifier\"}"));
    }

    private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token()));
    }

    private UserMusicAccount spotifyAccount(User user) {
        Users entity = userRepository.findByUsername(user.name()).orElseThrow();
        return accountRepository.findByUserAndProvider(entity, MusicProvider.SPOTIFY).orElseThrow();
    }

    private void expireAccessToken(User user) {
        UserMusicAccount account = spotifyAccount(user);
        account.setSpotifyAccessTokenExpiresAt(Instant.now().minusSeconds(5));
        accountRepository.save(account);
    }

    private static String tokens(String access, String refresh) {
        return "{\"access_token\":\"" + access + "\",\"refresh_token\":\"" + refresh
                + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";
    }

    /** Stand-in for accounts.spotify.com and api.spotify.com: canned responses per "METHOD /path", served in order. */
    static final class FakeSpotify {

        record Recorded(String key, String query, String authorization, String body) {
        }

        private record Canned(int status, String body) {
        }

        private final HttpServer server;
        private final Map<String, Deque<Canned>> canned = new ConcurrentHashMap<>();
        private final List<Recorded> recorded = new CopyOnWriteArrayList<>();

        private FakeSpotify(HttpServer server) {
            this.server = server;
        }

        static FakeSpotify start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                FakeSpotify fake = new FakeSpotify(server);
                server.createContext("/", fake::handle);
                server.start();
                return fake;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        void reset() {
            canned.clear();
            recorded.clear();
        }

        void respond(String key, int status, String body) {
            canned.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).add(new Canned(status, body));
        }

        Recorded last(String key) {
            List<Recorded> matching = all(key);
            if (matching.isEmpty()) {
                throw new AssertionError("Spotify didn't receive " + key);
            }
            return matching.get(matching.size() - 1);
        }

        List<Recorded> all(String key) {
            return recorded.stream().filter(request -> request.key().equals(key)).toList();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recorded.add(new Recorded(key, exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));

            Deque<Canned> queue = canned.get(key);
            Canned response = queue == null ? null : queue.poll();
            if (response == null) {
                response = new Canned(500, "{\"error\":\"no canned response for " + key + "\"}");
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
