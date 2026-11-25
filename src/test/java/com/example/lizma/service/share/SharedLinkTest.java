package com.example.lizma.service.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading what a shared link points at, from the link alone. */
class SharedLinkTest {

    private static final String TRACK = "0VjIjW4GlUZAMYd2vXMi3b";

    @Test
    void anAppleMusicSongInsideAnAlbumLink() {
        assertThat(SharedLink.parse("https://music.apple.com/ua/album/blinding-lights/1488408555?i=1488408568&l=uk"))
                .isEqualTo(new SharedLink.AppleSong("1488408568", "ua"));
    }

    @Test
    void anAppleMusicSongLink() {
        assertThat(SharedLink.parse("https://music.apple.com/us/song/blinding-lights/1488408568"))
                .isEqualTo(new SharedLink.AppleSong("1488408568", "us"));
        assertThat(SharedLink.parse("https://music.apple.com/song/1488408568"))
                .isEqualTo(new SharedLink.AppleSong("1488408568", null));
    }

    @Test
    void anOldItunesLink() {
        assertThat(SharedLink.parse("https://itunes.apple.com/gb/album/after-hours/id1488408555?i=1488408568&uo=4"))
                .isEqualTo(new SharedLink.AppleSong("1488408568", "gb"));
    }

    @Test
    void aSpotifyTrackWithItsTrackingParameter() {
        assertThat(SharedLink.parse("https://open.spotify.com/track/" + TRACK + "?si=4f2c1a"))
                .isEqualTo(new SharedLink.SpotifyTrack(TRACK));
        assertThat(SharedLink.parse("https://open.spotify.com/intl-de/track/" + TRACK))
                .isEqualTo(new SharedLink.SpotifyTrack(TRACK));
        assertThat(SharedLink.parse("spotify:track:" + TRACK)).isEqualTo(new SharedLink.SpotifyTrack(TRACK));
        assertThat(new SharedLink.SpotifyTrack(TRACK).url()).isEqualTo("https://open.spotify.com/track/" + TRACK);
    }

    @Test
    void theLinkInsideASharedSentence() {
        assertThat(SharedLink.parse("Listen to Blinding Lights on Spotify: https://open.spotify.com/track/" + TRACK + "."))
                .isEqualTo(new SharedLink.SpotifyTrack(TRACK));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://music.apple.com/ua/album/after-hours/1488408555",
            "https://music.apple.com/ua/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb",
            "https://music.apple.com/ua/artist/the-weeknd/479756766",
            "https://open.spotify.com/album/4yP0hdKOZPNshxUOjY0cZj",
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/episode/512ojhOuo1ktJprKbVcKyQ",
            "spotify:album:4yP0hdKOZPNshxUOjY0cZj",
    })
    void albumsPlaylistsArtistsAndPodcastsAreNotSongs(String link) {
        assertThat(SharedLink.parse(link)).isInstanceOf(SharedLink.NotASong.class);
    }

    @Test
    void shortLinksAreOpenedFirst() {
        assertThat(SharedLink.parse("https://spotify.link/aBcD3fGh"))
                .isEqualTo(new SharedLink.ShortLink("https://spotify.link/aBcD3fGh"));
        assertThat(SharedLink.parse("https://apple.co/3xYz")).isEqualTo(new SharedLink.ShortLink("https://apple.co/3xYz"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "just some words", "https://www.youtube.com/watch?v=4NRXx6U8ABQ",
            "https://open.spotify.com/", "https://open.spotify.com/track/short", "https://music.apple.com/ua/browse",
            "https://evil.example/open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"})
    void anythingElseIsUnsupported(String text) {
        assertThat(SharedLink.parse(text)).isInstanceOf(SharedLink.Unsupported.class);
    }

    @Test
    void titlesAreComparedWithoutTheirExtras() {
        assertThat(SharedLinkResolver.comparable("Here Comes the Sun - Remastered 2019"))
                .isEqualTo(SharedLinkResolver.comparable("Here Comes The Sun (Remastered 2019)"))
                .isEqualTo("here comes the sun");
        assertThat(SharedLinkResolver.comparable("Levitating (feat. DaBaby)")).isEqualTo("levitating");
        assertThat(SharedLinkResolver.comparable("Їжачок")).isEqualTo(SharedLinkResolver.comparable("ЇЖАЧОК"));
        assertThat(SharedLinkResolver.searchText("Levitating (feat. DaBaby) - Remix")).isEqualTo("Levitating");
        assertThat(SharedLinkResolver.searchText("(Untitled)")).isEqualTo("(Untitled)");
    }

    @Test
    void versionsAreToldApartByTheirTitles() {
        assertThat(SharedLinkResolver.versionWords("Bohemian Rhapsody - Remastered 2011")).isEmpty();
        assertThat(SharedLinkResolver.versionWords("Bohemian Rhapsody (Operatic Section / 2011 A Cappella Mix)"))
                .containsExactlyInAnyOrder("cappella", "mix");
        assertThat(SharedLinkResolver.versionWords("Song - Live at Wembley"))
                .isEqualTo(SharedLinkResolver.versionWords("Song (Live At Wembley Stadium / July 1986)"));
        assertThat(SharedLinkResolver.versionWords("Levitating (feat. Live Band)")).isEmpty(); // a guest, not a version
        assertThat(SharedLinkResolver.versionWords("Delivery")).isEmpty(); // whole words only
    }

    @Test
    void theAffiliateParameterIsDropped() {
        assertThat(SharedLinkResolver.withoutAffiliateParameter("https://music.apple.com/ua/album/x/1?i=2&uo=4"))
                .isEqualTo("https://music.apple.com/ua/album/x/1?i=2");
        assertThat(SharedLinkResolver.withoutAffiliateParameter("https://music.apple.com/ua/album/x/1?uo=4&i=2"))
                .isEqualTo("https://music.apple.com/ua/album/x/1?i=2");
        assertThat(SharedLinkResolver.withoutAffiliateParameter("https://music.apple.com/ua/song/x/2?uo=4"))
                .isEqualTo("https://music.apple.com/ua/song/x/2");
    }

    @Test
    void coversComeOnlyFromTheMusicServices() {
        assertThat(SharedLinkClient.isCoverUrl("https://is1-ssl.mzstatic.com/image/thumb/a/100x100bb.jpg")).isTrue();
        assertThat(SharedLinkClient.isCoverUrl("https://image-cdn-fa.spotifycdn.com/image/ab67616d")).isTrue();
        assertThat(SharedLinkClient.isCoverUrl("https://i.scdn.co/image/ab67616d")).isTrue();
        assertThat(SharedLinkClient.isCoverUrl("http://i.scdn.co/image/ab67616d")).isFalse();
        assertThat(SharedLinkClient.isCoverUrl("https://scdn.co.evil.example/x.jpg")).isFalse();
        assertThat(SharedLinkClient.isCoverUrl("https://localhost/x.jpg")).isFalse();
        assertThat(SharedLinkClient.isCoverUrl(null)).isFalse();
    }
}
