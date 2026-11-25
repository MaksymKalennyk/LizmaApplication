package com.example.lizma.service.share;

import com.example.lizma.exception.ExternalServiceException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.TooManyRequestsException;
import com.example.lizma.model.dto.ShareResolution;
import com.example.lizma.model.dto.SharedTrack;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Finds the song behind a link shared from Apple Music or Spotify (in the share extension, or pasted into the
 * composer), as an entry of Apple's catalog, the one every post comes from.
 * <ul>
 *   <li>An Apple Music link names the song: it's looked up by its id.</li>
 *   <li>A Spotify link gives only the track's title and cover: Spotify's API is closed to apps like this one, its
 *   public oEmbed isn't. Apple's catalog is searched for the title, and the song whose cover is the same picture
 *   wins, if its title names the same version (no "Live" or "Remix" the shared one doesn't have: versions on one
 *   album share its cover). Otherwise the likeliest songs are offered to choose from. The post keeps the exact
 *   Spotify track, so "Open in Spotify" opens what was shared.</li>
 * </ul>
 */
@Service
public class SharedLinkResolver {

    static final String NOT_A_SONG = "Only songs can be shared";
    static final String UNSUPPORTED = "Share a song from Apple Music or Spotify";
    static final String NOT_FOUND = "Couldn't find this song in Apple Music";
    static final String TOO_MANY = "You've shared a lot of links. Please try again later.";

    /**
     * Covers this close are the same picture. Measured on real covers: the same artwork 10 cells apart, or up to 41
     * when a remaster crops it a little differently; other artwork 70 and more.
     */
    static final int SAME_COVER = 48;
    /** At most this many songs of the same title have their covers compared. */
    static final int COMPARED = 20;
    /**
     * Words that make a recording another version of the song. Versions on the same album share its cover, so the
     * cover can't tell "Bohemian Rhapsody" from "Bohemian Rhapsody (A Cappella Mix)": these words in the title do.
     */
    private static final Set<String> VERSION_WORDS = Set.of("live", "acoustic", "remix", "mix", "edit", "instrumental",
            "demo", "cappella", "acapella", "karaoke", "unplugged", "orchestral", "sped", "slowed", "reverb", "extended",
            "long", "piano", "session");
    /** Songs offered when none is certain. */
    static final int CHOICES = 4;
    private static final int SEARCHED = 50;
    private static final int SHORT_LINK_HOPS = 3;

    private final SharedLinkClient client;
    private final int linksPerWindow;
    private final Duration window;
    private final Map<Long, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public SharedLinkResolver(SharedLinkClient client,
                              @Value("${app.share.links-per-window:30}") int linksPerWindow,
                              @Value("${app.share.window:10m}") Duration window) {
        this.client = client;
        this.linksPerWindow = linksPerWindow;
        this.window = window;
    }

    /**
     * The song a shared link (or a text with one) points at, for this user. {@code country} is the phone's region,
     * whose store is searched when the link doesn't name one.
     */
    public ShareResolution resolve(Long userId, String text, String country) {
        countRequest(userId);
        SharedLink link = SharedLink.parse(text);
        for (int hop = 0; hop < SHORT_LINK_HOPS && link instanceof SharedLink.ShortLink shortLink; hop++) {
            link = client.redirectTarget(shortLink.url()).map(SharedLink::parse).orElseGet(SharedLink.Unsupported::new);
        }
        String store = store(country);
        return switch (link) {
            case SharedLink.AppleSong song -> appleSong(song, store);
            case SharedLink.SpotifyTrack track -> spotifyTrack(track, store);
            case SharedLink.NotASong ignored -> throw new InvalidActionException(NOT_A_SONG);
            case SharedLink.ShortLink ignored -> throw new InvalidActionException(UNSUPPORTED);
            case SharedLink.Unsupported ignored -> throw new InvalidActionException(UNSUPPORTED);
        };
    }

    /** Looked up in the link's own store first: the song is surely there. */
    private ShareResolution appleSong(SharedLink.AppleSong song, String store) {
        for (String country : stores(song.storefront(), store)) {
            Optional<JsonNode> found = client.appleSong(song.id(), country);
            if (found.isPresent()) {
                return ShareResolution.exact(track(found.get(), null));
            }
        }
        throw new InvalidActionException(NOT_FOUND);
    }

    private ShareResolution spotifyTrack(SharedLink.SpotifyTrack shared, String store) {
        JsonNode spotify = client.spotifyTrack(shared.id()).orElseThrow(() -> new InvalidActionException(NOT_FOUND));
        String title = spotify.path("title").asText("").strip();
        if (title.isEmpty()) {
            throw new InvalidActionException(NOT_FOUND);
        }
        String wanted = comparable(title);
        List<JsonNode> sameTitle = client.appleSearch(searchText(title), store, SEARCHED).stream()
                .filter(song -> comparable(song.path("trackName").asText("")).equals(wanted))
                .limit(COMPARED)
                .toList();
        if (sameTitle.isEmpty()) {
            throw new InvalidActionException(NOT_FOUND);
        }
        Set<String> version = versionWords(title);
        // Songs with the same cover first, the closest first; the others keep Apple's order (their distances say
        // nothing more than "another picture"). Within each, the same version before other versions.
        List<Scored> ranked = scoreCovers(spotify.path("thumbnail_url").asText(null), sameTitle).stream()
                .map(scored -> scored.withSameVersion(versionWords(scored.song().path("trackName").asText("")).equals(version)))
                .sorted(Comparator.comparing((Scored scored) -> !scored.sameCover())
                        .thenComparing(scored -> !scored.sameVersion())
                        .thenComparingInt(scored -> scored.sameCover() ? scored.distance() : scored.order()))
                .toList();
        Scored best = ranked.get(0);
        if (best.sameCover() && best.sameVersion()) {
            return ShareResolution.exact(track(best.song(), shared.url()));
        }
        // Not sure: songs with the same cover first (another version of it, perhaps), then the other ones.
        return ShareResolution.choices(ranked.stream().limit(CHOICES).map(scored -> track(scored.song(), shared.url())).toList());
    }

    /** A song of the same title: its place in Apple's results, how far its cover is from Spotify's. */
    private record Scored(JsonNode song, int order, int distance, boolean sameVersion) {
        boolean sameCover() {
            return distance <= SAME_COVER;
        }

        Scored withSameVersion(boolean same) {
            return new Scored(song, order, distance, same);
        }
    }

    /** How far each song's cover is from Spotify's, in Apple's order; all the covers load at once. */
    private List<Scored> scoreCovers(String spotifyCover, List<JsonNode> songs) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Optional<CoverFingerprint>> wanted = pool.submit(() -> fingerprint(spotifyCover));
            List<Future<Optional<CoverFingerprint>>> covers = songs.stream()
                    .map(song -> pool.submit(() -> fingerprint(song.path("artworkUrl100").asText(null))))
                    .toList();
            Optional<CoverFingerprint> reference = wanted.get();
            List<Scored> scored = new ArrayList<>();
            for (int i = 0; i < songs.size(); i++) {
                Optional<CoverFingerprint> cover = covers.get(i).get();
                int distance = reference.isPresent() && cover.isPresent()
                        ? reference.get().distanceTo(cover.get()) : CoverFingerprint.CELLS;
                scored.add(new Scored(songs.get(i), i, distance, false));
            }
            return scored;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(SharedLinkClient.APPLE_DOWN);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Comparing covers failed", e.getCause());
        }
    }

    private Optional<CoverFingerprint> fingerprint(String url) {
        return client.cover(url).map(CoverFingerprint::of);
    }

    /** A title without its extras, for comparing: "Song - Remastered 2011", "Song (feat. X)" and "SONG" match. */
    static String comparable(String title) {
        String plain = Normalizer.normalize(searchText(title).toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /** The words of a title that make it a version of the song ("live", "acoustic"…), leaving out who it features. */
    static Set<String> versionWords(String title) {
        String withoutGuests = title.replaceAll("(?i)[(\\[](feat|ft|with)\\.?\\s[^)\\]]*[)\\]]", " ");
        String plain = Normalizer.normalize(withoutGuests.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        Set<String> words = new TreeSet<>();
        for (String word : plain.split("[^\\p{L}\\p{N}]+")) {
            if (VERSION_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /** What to search for: the title without " - Remastered 2011", "(feat. …)" and the like. */
    static String searchText(String title) {
        String text = title.replaceAll("\\s+-\\s+.*$", "").replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "").strip();
        return text.isEmpty() ? title.strip() : text;
    }

    private static SharedTrack track(JsonNode song, String spotifyUrl) {
        return new SharedTrack(
                song.path("trackId").asText(),
                song.path("trackName").asText(),
                song.path("artistName").asText(),
                text(song, "collectionName"),
                largeCover(text(song, "artworkUrl100")),
                text(song, "previewUrl"),
                withoutAffiliateParameter(text(song, "trackViewUrl")),
                spotifyUrl);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /** The cover at 600×600: the API names the 100×100 one, and the image server makes any size of the same path. */
    private static String largeCover(String url) {
        return url == null ? null : url.replace("100x100bb", "600x600bb");
    }

    /** The song's Apple Music page without the affiliate parameter ({@code uo}). */
    static String withoutAffiliateParameter(String url) {
        return url == null ? null : url.replaceAll("([?&])uo=\\d+&?", "$1").replaceAll("[?&]$", "");
    }

    private static String store(String country) {
        String code = country == null ? "" : country.strip();
        return code.matches("[A-Za-z]{2}") ? code.toLowerCase(Locale.ROOT) : "us";
    }

    private static List<String> stores(String fromLink, String own) {
        Set<String> stores = new LinkedHashSet<>();
        if (fromLink != null) {
            stores.add(fromLink);
        }
        stores.add(own);
        stores.add("us");
        return List.copyOf(stores);
    }

    /** At most {@code linksPerWindow} links per user in the sliding window: each one costs calls to Apple and Spotify. */
    private void countRequest(Long userId) {
        Deque<Instant> times = recent.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            Instant now = Instant.now();
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(window))) {
                times.pollFirst();
            }
            if (times.size() >= linksPerWindow) {
                throw new TooManyRequestsException(TOO_MANY);
            }
            times.addLast(now);
        }
    }
}
