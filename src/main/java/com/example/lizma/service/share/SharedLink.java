package com.example.lizma.service.share;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a link shared from a music app points at, read from the link alone: a song in Apple Music, a track in
 * Spotify, a short link that has to be opened first, or something that isn't a song.
 */
public sealed interface SharedLink {

    /** A song in Apple's catalog; {@code storefront} is the store in the link ("ua", "us"), or null. */
    record AppleSong(String id, String storefront) implements SharedLink {
    }

    /** A track in Spotify, by its 22-character id. */
    record SpotifyTrack(String id) implements SharedLink {
        /** The track's page without the tracking parameters of the shared link. */
        public String url() {
            return "https://open.spotify.com/track/" + id;
        }
    }

    /** spotify.link, spoti.fi, apple.co: where it leads is known only by asking. */
    record ShortLink(String url) implements SharedLink {
    }

    /** An album, a playlist, an artist, a podcast…: only songs are posted. */
    record NotASong() implements SharedLink {
    }

    /** No link from Apple Music or Spotify. */
    record Unsupported() implements SharedLink {
    }

    Pattern ANY_URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    Pattern SPOTIFY_URI = Pattern.compile("spotify:(track|album|playlist|artist|episode|show):([A-Za-z0-9]{22})");
    Pattern SPOTIFY_ID = Pattern.compile("[A-Za-z0-9]{22}");
    Pattern APPLE_ID = Pattern.compile("(?:id)?(\\d{1,15})");
    Set<String> SHORT_LINK_HOSTS = Set.of("spotify.link", "spoti.fi", "apple.co");
    Set<String> SPOTIFY_NOT_SONGS = Set.of("album", "playlist", "artist", "episode", "show", "user", "genre", "collection");
    Set<String> APPLE_NOT_SONGS = Set.of("playlist", "artist", "music-video", "station", "curator", "room", "podcast");

    /** The first link in a shared text: the share sheet may put words around it ("Listen to … on Spotify"). */
    static SharedLink parse(String text) {
        if (text == null || text.isBlank()) {
            return new Unsupported();
        }
        Matcher spotifyUri = SPOTIFY_URI.matcher(text);
        if (spotifyUri.find()) {
            return "track".equals(spotifyUri.group(1)) ? new SpotifyTrack(spotifyUri.group(2)) : new NotASong();
        }
        Matcher found = ANY_URL.matcher(text);
        if (!found.find()) {
            return new Unsupported();
        }
        // A link at the end of a sentence keeps the sentence's punctuation.
        String raw = found.group().replaceAll("[).,!?;:]+$", "");
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            return new Unsupported();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        List<String> path = uri.getPath() == null ? List.of()
                : Arrays.stream(uri.getPath().split("/")).filter(part -> !part.isBlank()).toList();
        if (SHORT_LINK_HOSTS.contains(host)) {
            return path.isEmpty() ? new Unsupported() : new ShortLink("https://" + host + uri.getRawPath());
        }
        if (host.equals("open.spotify.com") || host.equals("play.spotify.com")) {
            return spotify(path);
        }
        if (host.equals("music.apple.com") || host.endsWith(".music.apple.com") || host.equals("itunes.apple.com")) {
            return apple(path, uri.getRawQuery());
        }
        return new Unsupported();
    }

    /** open.spotify.com/track/{id}, also with a language part first (/intl-de/track/{id}). */
    private static SharedLink spotify(List<String> path) {
        List<String> rest = !path.isEmpty() && path.get(0).startsWith("intl-") ? path.subList(1, path.size()) : path;
        if (rest.size() < 2) {
            return new Unsupported();
        }
        String type = rest.get(0).toLowerCase(Locale.ROOT);
        if (type.equals("track") && SPOTIFY_ID.matcher(rest.get(1)).matches()) {
            return new SpotifyTrack(rest.get(1));
        }
        return SPOTIFY_NOT_SONGS.contains(type) ? new NotASong() : new Unsupported();
    }

    /**
     * music.apple.com/{store}/album/{name}/{albumId}?i={songId}, music.apple.com/{store}/song/{name}/{songId};
     * the store and the name may be missing, old itunes.apple.com links put "id" before the numbers.
     */
    private static SharedLink apple(List<String> path, String rawQuery) {
        List<String> rest = path;
        String storefront = null;
        if (!rest.isEmpty() && rest.get(0).matches("(?i)[a-z]{2}")) {
            storefront = rest.get(0).toLowerCase(Locale.ROOT);
            rest = rest.subList(1, rest.size());
        }
        if (rest.isEmpty()) {
            return new Unsupported();
        }
        String type = rest.get(0).toLowerCase(Locale.ROOT);
        if (type.equals("album")) {
            String song = queryParameter(rawQuery, "i");
            return song != null && song.matches("\\d{1,15}") ? new AppleSong(song, storefront) : new NotASong();
        }
        if (type.equals("song")) {
            Matcher id = APPLE_ID.matcher(rest.get(rest.size() - 1));
            return rest.size() >= 2 && id.matches() ? new AppleSong(id.group(1), storefront) : new Unsupported();
        }
        return APPLE_NOT_SONGS.contains(type) ? new NotASong() : new Unsupported();
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
