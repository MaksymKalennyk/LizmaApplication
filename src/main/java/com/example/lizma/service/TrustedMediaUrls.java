package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Links that come with a post (cover, preview, Apple Music page) must point at the music services' own
 * servers over HTTPS. Otherwise a post could make every friend's app load anything from anywhere: an
 * offensive picture, or a tracking pixel that reveals who looked at it.
 */
final class TrustedMediaUrls {

    private static final List<String> COVER_HOSTS = List.of("mzstatic.com", "scdn.co", "spotifycdn.com");
    private static final List<String> PREVIEW_HOSTS = List.of("apple.com", "mzstatic.com");
    private static final List<String> APPLE_MUSIC_HOSTS = List.of("music.apple.com", "itunes.apple.com");
    private static final Pattern SPOTIFY_TRACK = Pattern.compile("https://open\\.spotify\\.com/track/[A-Za-z0-9]{22}");

    private TrustedMediaUrls() {
    }

    static String cover(String url) {
        return check(url, COVER_HOSTS);
    }

    static String preview(String url) {
        return check(url, PREVIEW_HOSTS);
    }

    static String appleMusic(String url) {
        return check(url, APPLE_MUSIC_HOSTS);
    }

    /** A Spotify track's own page, exactly as the share resolver writes it (no tracking parameters). */
    static String spotifyTrack(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.strip();
        if (SPOTIFY_TRACK.matcher(trimmed).matches()) {
            return trimmed;
        }
        throw new InvalidActionException("This link isn't allowed in a post");
    }

    /** Null and blank pass as null; anything else must be https on one of the hosts (or a subdomain). */
    private static String check(String url, List<String> hosts) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.strip();
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean trusted = "https".equalsIgnoreCase(uri.getScheme())
                    && hosts.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
            if (trusted && uri.getUserInfo() == null) {
                return trimmed;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the error below
        }
        throw new InvalidActionException("This link isn't allowed in a post");
    }
}
