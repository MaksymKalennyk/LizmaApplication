package com.example.lizma.service;

import com.example.lizma.model.Post;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds a posted track in the other services, so everyone can open it where they listen: Spotify from an
 * Apple Music track and the other way round (song.link), plus Apple's 30-second preview for Spotify posts
 * (iTunes lookup). Runs after a post is saved and, for posts it couldn't do yet, every few minutes.
 * <p>
 * song.link retired its keyless API in 2026 (calls without a key get 401 PUBLIC_API_ACCESS_DEPRECATED) and
 * gives keys only on request, so lookups run only with {@code app.links.odesli-key}. Without one, posts keep
 * the links the app sent (Apple Music) and the app opens Spotify's search for the song.
 * <p>
 * Only the track's public link is sent, never who posted it. A failed or rate-limited lookup is tried again
 * later; a refused key stops the lookups until the next start.
 */
@Slf4j
@Service
public class MusicLinkResolver {

    private static final int BACKFILL_BATCH = 4;

    private final PostRepository postRepository;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final boolean enabled;
    private final String odesliKey;
    private final String odesliUrl;
    private final String itunesLookupUrl;
    /** Set when song.link refused the key: no point asking again until the key is changed. */
    private final AtomicBoolean keyRefused = new AtomicBoolean();

    public MusicLinkResolver(PostRepository postRepository, TransactionTemplate transactions, ObjectMapper objectMapper,
                             @Value("${app.links.enabled:true}") boolean enabled,
                             @Value("${app.links.odesli-key:}") String odesliKey,
                             @Value("${app.links.odesli-url:https://api.song.link/v1-alpha.1/links}") String odesliUrl,
                             @Value("${app.links.itunes-lookup-url:https://itunes.apple.com/lookup}") String itunesLookupUrl) {
        this.postRepository = postRepository;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.odesliKey = odesliKey == null ? "" : odesliKey.trim();
        this.enabled = enabled && !this.odesliKey.isEmpty();
        this.odesliUrl = odesliUrl;
        this.itunesLookupUrl = itunesLookupUrl;
        if (enabled && this.odesliKey.isEmpty()) {
            log.info("Links to other music services are not looked up: song.link needs a key (ODESLI_API_KEY)");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        if (enabled) {
            resolve(event.postId());
        }
    }

    @Scheduled(fixedDelayString = "${app.links.backfill-interval:PT2M}", initialDelayString = "${app.links.backfill-initial-delay:PT1M}")
    public void backfill() {
        if (!enabled) {
            return;
        }
        List<Long> ids = transactions.execute(status ->
                postRepository.findIdsWithoutLinks(PageRequest.of(0, BACKFILL_BATCH)));
        if (ids != null) {
            ids.forEach(this::resolve);
        }
    }

    /** Looks the post's track up and fills in what was missing. Returns false if it should be tried again. */
    public boolean resolve(Long postId) {
        if (keyRefused.get()) {
            return false;
        }
        Post post = transactions.execute(status -> postRepository.findById(postId).orElse(null));
        if (post == null || post.getLinksResolvedAt() != null) {
            return true;
        }
        Found found;
        try {
            found = lookUp(post);
        } catch (IOException | RuntimeException e) {
            log.info("Couldn't look up the links of post #{} yet: {}", postId, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (found == null) {
            return false; // rate limited or the service is down: next run
        }
        transactions.executeWithoutResult(status -> postRepository.findById(postId).ifPresent(saved -> {
            if (saved.getSpotifyUrl() == null) {
                saved.setSpotifyUrl(found.spotifyUrl());
            }
            if (saved.getAppleMusicUrl() == null) {
                saved.setAppleMusicUrl(found.appleMusicUrl());
            }
            if (saved.getPreviewUrl() == null) {
                saved.setPreviewUrl(found.previewUrl());
            }
            saved.setLinksResolvedAt(Instant.now());
        }));
        return true;
    }

    private record Found(String spotifyUrl, String appleMusicUrl, String previewUrl) {
    }

    /** Null when the service didn't answer properly (try again later); empty fields when it doesn't know the track. */
    private Found lookUp(Post post) throws IOException, InterruptedException {
        String query = sourceQuery(post);
        if (query == null) {
            return new Found(null, null, null);
        }
        HttpResponse<String> response = get(odesliUrl + "?" + query + "&songIfSingle=true"
                + (odesliKey.isEmpty() ? "" : "&key=" + encode(odesliKey)));
        if (response.statusCode() == 404 || response.statusCode() == 400) {
            return new Found(null, null, null); // song.link doesn't know it: nothing to retry
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            if (keyRefused.compareAndSet(false, true)) {
                log.warn("song.link refused the key ({} {}): links aren't looked up until the next start. Check ODESLI_API_KEY.",
                        response.statusCode(), errorCode(response));
            }
            return null;
        }
        if (response.statusCode() != 200) {
            log.info("song.link answered {} {}: post #{} is tried again later",
                    response.statusCode(), errorCode(response), post.getId());
            return null;
        }
        JsonNode links = objectMapper.readTree(response.body()).path("linksByPlatform");
        String spotifyUrl = trusted(links.path("spotify").path("url").asText(null), "open.spotify.com");
        String appleMusicUrl = trusted(links.path("appleMusic").path("url").asText(null), "music.apple.com");
        String previewUrl = null;
        if (post.getPreviewUrl() == null) {
            String appleId = appleId(post, links.path("appleMusic").path("entityUniqueId").asText(""));
            if (appleId != null) {
                previewUrl = previewFromItunes(appleId);
            }
        }
        return new Found(spotifyUrl, appleMusicUrl, previewUrl);
    }

    private String sourceQuery(Post post) {
        if (post.getMusicProvider() == MusicProvider.SPOTIFY) {
            String spotify = PostService.spotifyTrackUrl(post);
            return spotify == null ? null : "url=" + encode(spotify);
        }
        if (post.getAppleMusicUrl() != null) {
            return "url=" + encode(post.getAppleMusicUrl());
        }
        return post.getMusicId().matches("\\d+") ? "platform=appleMusic&type=song&id=" + post.getMusicId() : null;
    }

    private static String appleId(Post post, String entityUniqueId) {
        if (post.getMusicProvider() == MusicProvider.APPLE && post.getMusicId().matches("\\d+")) {
            return post.getMusicId();
        }
        // e.g. "ITUNES_SONG::1440857781"
        int separator = entityUniqueId.lastIndexOf("::");
        String id = separator < 0 ? "" : entityUniqueId.substring(separator + 2);
        return id.matches("\\d+") ? id : null;
    }

    private String previewFromItunes(String appleId) throws IOException, InterruptedException {
        HttpResponse<String> response = get(itunesLookupUrl + "?entity=song&id=" + appleId);
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode results = objectMapper.readTree(response.body()).path("results");
        for (JsonNode result : results) {
            String preview = result.path("previewUrl").asText(null);
            if (preview != null) {
                try {
                    return TrustedMediaUrls.preview(preview);
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Only links to the service's own site are kept. */
    private static String trusted(String url, String host) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String actual = uri.getHost() == null ? "" : uri.getHost();
            boolean ok = "https".equals(uri.getScheme()) && (actual.equals(host) || actual.endsWith("." + host));
            return ok && url.length() <= 512 ? url : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The error code song.link puts in its answer, e.g. "INVALID_ACCESS_KEY"; empty if there is none. */
    private String errorCode(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body()).path("code").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
