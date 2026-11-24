package com.example.lizma;

import com.example.lizma.model.Post;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.service.MusicLinkResolver;
import com.example.lizma.service.PostCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 4: posts from the Apple Music catalog, links to the other services, and only trusted links. */
@SpringBootTest(properties = {
        // Lookups are run by the test itself, not by the background job.
        "app.links.enabled=false",
        "app.links.odesli-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:musiclinks;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MusicLinksTest extends ApiTestSupport {

    private static final FakeServices SERVICES = FakeServices.start();

    @DynamicPropertySource
    static void links(DynamicPropertyRegistry registry) {
        registry.add("app.links.odesli-url", () -> SERVICES.baseUrl() + "/links");
        registry.add("app.links.itunes-lookup-url", () -> SERVICES.baseUrl() + "/lookup");
    }

    @AfterAll
    static void stop() {
        SERVICES.server.stop(0);
    }

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TransactionTemplate transactions;

    private MusicLinkResolver resolver;

    @Autowired
    void resolver(MusicLinkResolver resolver) {
        this.resolver = resolver;
    }

    @BeforeEach
    void clear() {
        SERVICES.requests.clear();
        SERVICES.status = 200;
    }

    @Test
    void anAppleMusicPostGetsItsSpotifyLink() throws Exception {
        User author = newUser();
        JsonNode created = json(as(author, post("/posts").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                "musicId", "1440857781", "musicProvider", "APPLE", "trackName", "Blinding Lights", "artistName", "The Weeknd",
                "albumCoverUrl", "https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg",
                "previewUrl", "https://audio-ssl.itunes.apple.com/itunes-assets/preview.m4a",
                "appleMusicUrl", "https://music.apple.com/us/album/blinding-lights/1499378108?i=1440857781"))))
                .andExpect(status().isOk()));
        assertThat(created.get("previewUrl").asText()).endsWith("preview.m4a");
        assertThat(created.get("spotifyUrl").isNull()).isTrue();

        assertThat(resolver.resolve(created.get("id").asLong())).isTrue();

        assertThat(SERVICES.requests).hasSize(1);
        assertThat(SERVICES.requests.get(0)).startsWith("/links?url=https%3A%2F%2Fmusic.apple.com").endsWith("&key=test-key");
        as(author, get("/posts/" + created.get("id").asLong()))
                .andExpect(jsonPath("$.spotifyUrl").value("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"))
                .andExpect(jsonPath("$.appleMusicUrl").value("https://music.apple.com/us/album/blinding-lights/1499378108?i=1440857781"));
    }

    @Test
    void anOldSpotifyPostGetsAppleMusicAndAPreview() throws Exception {
        User author = newUser();
        long postId = createPost(author, "old one"); // musicId "track-1" is not a real id: no lookup at all
        Post post = postRepository.findById(postId).orElseThrow();
        post.setMusicId("0VjIjW4GlUZAMYd2vXMi3b");
        postRepository.save(post);

        assertThat(resolver.resolve(postId)).isTrue();

        assertThat(SERVICES.requests).containsExactly(
                "/links?url=https%3A%2F%2Fopen.spotify.com%2Ftrack%2F0VjIjW4GlUZAMYd2vXMi3b&songIfSingle=true&key=test-key",
                "/lookup?entity=song&id=1440857781");
        as(author, get("/posts/" + postId))
                .andExpect(jsonPath("$.spotifyUrl").value("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"))
                .andExpect(jsonPath("$.appleMusicUrl").value(org.hamcrest.Matchers.startsWith("https://geo.music.apple.com/")))
                .andExpect(jsonPath("$.previewUrl").value("https://audio-ssl.itunes.apple.com/itunes-assets/found.m4a"));
        // Done once: a second run doesn't ask again.
        resolver.resolve(postId);
        assertThat(SERVICES.requests).hasSize(2);
    }

    @Test
    void whenTheServiceRefusesTheLookupIsTriedAgainLater() throws Exception {
        User author = newUser();
        long postId = createAppleMusicPost(author);
        SERVICES.status = 429;
        assertThat(resolver.resolve(postId)).isFalse();
        assertThat(postRepository.findById(postId).orElseThrow().getLinksResolvedAt()).isNull();

        SERVICES.status = 200;
        assertThat(resolver.resolve(postId)).isTrue();
        assertThat(postRepository.findById(postId).orElseThrow().getSpotifyUrl()).isNotNull();
    }

    @Test
    void withoutAKeyNothingIsLookedUp() throws Exception {
        // song.link answers every call without a key with 401 since 2026: don't even ask.
        MusicLinkResolver keyless = new MusicLinkResolver(postRepository, transactions, objectMapper, true, " ",
                SERVICES.baseUrl() + "/links", SERVICES.baseUrl() + "/lookup");
        User author = newUser();
        long postId = createAppleMusicPost(author);

        keyless.onPostCreated(new PostCreatedEvent(postId, author.id()));
        keyless.backfill();

        assertThat(SERVICES.requests).isEmpty();
    }

    @Test
    void aRefusedKeyStopsTheLookups() throws Exception {
        MusicLinkResolver withWrongKey = new MusicLinkResolver(postRepository, transactions, objectMapper, true, "wrong",
                SERVICES.baseUrl() + "/links", SERVICES.baseUrl() + "/lookup");
        long postId = createAppleMusicPost(newUser());
        SERVICES.status = 401;
        assertThat(withWrongKey.resolve(postId)).isFalse();

        SERVICES.status = 200;
        assertThat(withWrongKey.resolve(postId)).isFalse();
        assertThat(SERVICES.requests).hasSize(1);
        assertThat(postRepository.findById(postId).orElseThrow().getLinksResolvedAt()).isNull();
    }

    @Test
    void onlyTheServicesOwnLinksAreAccepted() throws Exception {
        User author = newUser();
        for (Map.Entry<String, String> bad : Map.of(
                "albumCoverUrl", "https://evil.example.com/cover.jpg",
                "previewUrl", "http://audio-ssl.itunes.apple.com/plain-http.m4a",
                "appleMusicUrl", "https://music.apple.com.evil.example.com/x").entrySet()) {
            Map<String, Object> body = new java.util.HashMap<>(Map.of("musicId", "1", "musicProvider", "APPLE",
                    "trackName", "T", "artistName", "A"));
            body.put(bad.getKey(), bad.getValue());
            as(author, post("/posts").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This link isn't allowed in a post"));
        }
    }

    private long createAppleMusicPost(User author) throws Exception {
        return json(as(author, post("/posts").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                "musicId", "1440857781", "musicProvider", "APPLE", "trackName", "Blinding Lights", "artistName", "The Weeknd",
                "appleMusicUrl", "https://music.apple.com/us/album/x/1?i=1440857781"))))
                .andExpect(status().isOk())).get("id").asLong();
    }

    /** song.link and iTunes lookup, answering the way the real ones do (trimmed down). */
    static final class FakeServices {
        final HttpServer server;
        final List<String> requests = new CopyOnWriteArrayList<>();
        volatile int status = 200;

        private FakeServices(HttpServer server) {
            this.server = server;
        }

        static FakeServices start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                FakeServices fake = new FakeServices(server);
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

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestURI().toString());
            String body;
            if (status != 200) {
                body = "{}";
            } else if (path.equals("/links")) {
                body = """
                        {"linksByPlatform": {
                          "spotify": {"url": "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
                                      "entityUniqueId": "SPOTIFY_SONG::0VjIjW4GlUZAMYd2vXMi3b"},
                          "appleMusic": {"url": "https://geo.music.apple.com/us/album/_/1499378108?i=1440857781",
                                         "entityUniqueId": "ITUNES_SONG::1440857781"},
                          "youtube": {"url": "https://www.youtube.com/watch?v=x"}
                        }}""";
            } else if (path.equals("/lookup")) {
                body = """
                        {"resultCount": 1, "results": [{"trackId": 1440857781,
                          "previewUrl": "https://audio-ssl.itunes.apple.com/itunes-assets/found.m4a"}]}""";
            } else {
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
