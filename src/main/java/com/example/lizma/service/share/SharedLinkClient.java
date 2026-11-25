package com.example.lizma.service.share;

import com.example.lizma.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The only place that asks Apple and Spotify about shared links: Apple's catalog (the free iTunes lookup and
 * search, no key), Spotify's public oEmbed (a track's title and cover, no key), the covers themselves, and where a
 * short link leads. Nothing about the user is sent, only the song.
 */
@Slf4j
@Component
public class SharedLinkClient {

    static final String APPLE_DOWN = "Apple Music isn't answering right now. Try again in a minute.";
    static final String SPOTIFY_DOWN = "Spotify isn't answering right now. Try again in a minute.";

    /** Covers come only from the music services' own image servers. */
    private static final List<String> COVER_HOSTS = List.of("mzstatic.com", "scdn.co", "spotifycdn.com");
    private static final int MAX_COVER_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json;
    private final String itunesUrl;
    private final String spotifyUrl;

    public SharedLinkClient(ObjectMapper json,
                            @Value("${app.share.itunes-url:https://itunes.apple.com}") String itunesUrl,
                            @Value("${app.share.spotify-url:https://open.spotify.com}") String spotifyUrl) {
        this.json = json;
        this.itunesUrl = itunesUrl;
        this.spotifyUrl = spotifyUrl;
    }

    /** The song with this id in this store's catalog, if the store has it. */
    public Optional<JsonNode> appleSong(String id, String country) {
        JsonNode body = getJson(itunesUrl + "/lookup?entity=song&id=" + encode(id) + "&country=" + encode(country), APPLE_DOWN);
        for (JsonNode result : body.path("results")) {
            if ("song".equals(result.path("kind").asText())) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    /** Songs in this store's catalog for a search text, best first. */
    public List<JsonNode> appleSearch(String term, String country, int limit) {
        JsonNode body = getJson(itunesUrl + "/search?media=music&entity=song&term=" + encode(term)
                + "&country=" + encode(country) + "&limit=" + limit, APPLE_DOWN);
        List<JsonNode> songs = new ArrayList<>();
        body.path("results").forEach(result -> {
            if ("song".equals(result.path("kind").asText())) {
                songs.add(result);
            }
        });
        return songs;
    }

    /** A Spotify track's title and cover ({@code title}, {@code thumbnail_url}); empty if Spotify doesn't know it. */
    public Optional<JsonNode> spotifyTrack(String trackId) {
        String page = "https://open.spotify.com/track/" + trackId;
        HttpResponse<byte[]> response = get(spotifyUrl + "/oembed?url=" + encode(page), SPOTIFY_DOWN);
        if (response.statusCode() == 404 || response.statusCode() == 400) {
            return Optional.empty();
        }
        return Optional.of(parse(response, SPOTIFY_DOWN));
    }

    /** Where a short link leads, one step at a time; empty when it doesn't redirect. */
    public Optional<String> redirectTarget(String shortLink) {
        try {
            HttpResponse<Void> response = http.send(request(shortLink), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                return response.headers().firstValue("Location").map(location -> URI.create(shortLink).resolve(location).toString());
            }
        } catch (IOException | IllegalArgumentException e) {
            log.info("Couldn't open the short link {}: {}", shortLink, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    /** A cover picture from the music services' image servers; empty when it can't be had. */
    public Optional<BufferedImage> cover(String url) {
        if (!isCoverUrl(url)) {
            return Optional.empty();
        }
        try {
            HttpResponse<InputStream> response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    return Optional.empty();
                }
                byte[] bytes = body.readNBytes(MAX_COVER_BYTES + 1);
                if (bytes.length > MAX_COVER_BYTES) {
                    return Optional.empty();
                }
                return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(bytes)));
            }
        } catch (IOException | IllegalArgumentException e) {
            log.info("Couldn't load the cover {}: {}", url, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    static boolean isCoverUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && COVER_HOSTS.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private JsonNode getJson(String url, String unavailable) {
        return parse(get(url, unavailable), unavailable);
    }

    private HttpResponse<byte[]> get(String url, String unavailable) {
        try {
            return http.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            log.warn("No answer from {}: {}", url, e.toString());
            throw new ExternalServiceException(unavailable);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(unavailable);
        }
    }

    private JsonNode parse(HttpResponse<byte[]> response, String unavailable) {
        if (response.statusCode() != 200) {
            log.warn("{} answered {}", response.uri(), response.statusCode());
            throw new ExternalServiceException(unavailable);
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            log.warn("{} answered something that isn't JSON", response.uri());
            throw new ExternalServiceException(unavailable);
        }
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "Lizma")
                .GET()
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
