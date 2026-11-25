package com.example.lizma;

import com.example.lizma.repo.ExtensionKeyRepository;
import com.example.lizma.service.share.SharedLinkClient;
import com.example.lizma.service.share.TestCovers;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Posting a song shared from Apple Music or Spotify: the share extension's key, finding the song from the link
 * (Apple and Spotify are stood in for), and the post it makes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:share;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.share.links-per-window=5"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SyncBackgroundWork.class)
class ShareIntegrationTest extends ApiTestSupport {

    private static final String APPLE_LINK = "https://music.apple.com/ua/album/blinding-lights/1488408555?i=1488408568";
    private static final String SPOTIFY_ID = "0VjIjW4GlUZAMYd2vXMi3b";
    private static final String SPOTIFY_LINK = "https://open.spotify.com/track/" + SPOTIFY_ID + "?si=4f2c1a";
    private static final String SPOTIFY_THUMBNAIL = "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e02blinding";

    @MockBean
    private SharedLinkClient links;
    @Autowired
    private ExtensionKeyRepository extensionKeys;

    /** A user with a session of their own: the refresh token a share key is tied to. */
    private record Phone(User user, String refreshToken) {
    }

    @Test
    void anAppleMusicLinkIsTheSongItNames() throws Exception {
        Phone me = phone();
        String key = shareKey(me);
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));

        JsonNode found = json(resolve(key, APPLE_LINK).andExpect(status().isOk()));
        assertThat(found.get("matched").asBoolean()).isTrue();
        JsonNode track = found.get("tracks").get(0);
        assertThat(track.get("musicId").asText()).isEqualTo("1488408568");
        assertThat(track.get("trackName").asText()).isEqualTo("Blinding Lights");
        assertThat(track.get("artistName").asText()).isEqualTo("The Weeknd");
        assertThat(track.get("albumCoverUrl").asText()).endsWith("/after-hours/600x600bb.jpg");
        assertThat(track.get("appleMusicUrl").asText()) // without Apple's affiliate parameter
                .isEqualTo("https://music.apple.com/ua/album/after-hours/1488408555?i=1488408568");
        assertThat(track.get("spotifyUrl").isNull()).isTrue();
        verify(links, never()).spotifyTrack(anyString());
    }

    @Test
    void theSharedSongIsPostedLikeAnyOther() throws Exception {
        Phone me = phone();
        User friend = newUser();
        makeFriends(me.user(), friend);
        String key = shareKey(me);
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));
        JsonNode track = json(resolve(key, APPLE_LINK)).get("tracks").get(0);

        JsonNode posted = json(share(key, postOf(track, "On repeat")).andExpect(status().isOk()));
        assertThat(posted.get("authorUsername").asText()).isEqualTo(me.user().name());
        assertThat(posted.get("caption").asText()).isEqualTo("On repeat");

        JsonNode feed = json(as(friend, get("/posts/feed")).andExpect(status().isOk()));
        assertThat(feed).anySatisfy(post -> {
            assertThat(post.get("id").asLong()).isEqualTo(posted.get("id").asLong());
            assertThat(post.get("trackName").asText()).isEqualTo("Blinding Lights");
            assertThat(post.get("appleMusicUrl").asText()).startsWith("https://music.apple.com/");
        });
    }

    @Test
    void aSpotifyLinkFindsTheAppleSongWithTheSameCover() throws Exception {
        Phone me = phone();
        String key = shareKey(me);
        BufferedImage cover = TestCovers.cover(1);
        spotifyTrack("Blinding Lights", cover);
        when(links.appleSearch("Blinding Lights", "ua", 50)).thenReturn(List.of(
                appleSong(1L, "Blinding Lights", "Jump Singers", "Blinding Lights - Single", "jump"),
                appleSong(2L, "Blinding Lights (Remix)", "The Weeknd & ROSALÍA", "Remix - Single", "remix"),
                appleSong(3L, "Blinding Lights", "The Weeknd", "After Hours (Deluxe)", "deluxe"),
                appleSong(4L, "Blinding Lights", "The Weeknd", "After Hours", "after-hours")));
        artwork("jump", TestCovers.cover(2));
        artwork("deluxe", TestCovers.cover(3));
        artwork("after-hours", cover);

        JsonNode found = json(resolve(key, SPOTIFY_LINK).andExpect(status().isOk()));
        assertThat(found.get("matched").asBoolean()).isTrue();
        JsonNode track = found.get("tracks").get(0);
        assertThat(track.get("musicId").asText()).isEqualTo("4");
        assertThat(track.get("artistName").asText()).isEqualTo("The Weeknd");
        // The exact track that was shared, without the link's tracking parameter.
        assertThat(track.get("spotifyUrl").asText()).isEqualTo("https://open.spotify.com/track/" + SPOTIFY_ID);

        JsonNode posted = json(share(key, postOf(track, null)).andExpect(status().isOk()));
        assertThat(posted.get("spotifyUrl").asText()).isEqualTo("https://open.spotify.com/track/" + SPOTIFY_ID);
    }

    @Test
    void whenNoCoverMatchesTheLikeliestSongsAreOffered() throws Exception {
        Phone me = phone();
        String key = shareKey(me);
        spotifyTrack("Home", TestCovers.cover(10));
        List<JsonNode> songs = List.of(
                appleSong(11L, "Home", "Artist One", "One", "one"),
                appleSong(12L, "Home - Live", "Artist Two", "Two", "two"),
                appleSong(13L, "Homeless", "Artist Three", "Three", "three"),
                appleSong(14L, "Home", "Artist Four", "Four", "four"),
                appleSong(15L, "Home", "Artist Five", "Five", "five"),
                appleSong(16L, "Home", "Artist Six", "Six", "six"));
        when(links.appleSearch("Home", "ua", 50)).thenReturn(songs);

        JsonNode found = json(resolve(key, SPOTIFY_LINK).andExpect(status().isOk()));
        assertThat(found.get("matched").asBoolean()).isFalse();
        // The same title in Apple's order, other versions ("Live") after; "Homeless" isn't the same title.
        assertThat(found.get("tracks")).extracting(track -> track.get("musicId").asText())
                .containsExactly("11", "14", "15", "16");
        assertThat(found.get("tracks")).allSatisfy(track ->
                assertThat(track.get("spotifyUrl").asText()).isEqualTo("https://open.spotify.com/track/" + SPOTIFY_ID));
    }

    @Test
    void amongVersionsWithTheSameCoverTheOneTitledLikeSpotifysWins() throws Exception {
        String key = shareKey(phone());
        BufferedImage album = TestCovers.cover(20);
        spotifyTrack("Bohemian Rhapsody - Remastered 2011", album);
        when(links.appleSearch("Bohemian Rhapsody", "ua", 50)).thenReturn(List.of(
                appleSong(21L, "Bohemian Rhapsody (Live Aid)", "Queen", "Soundtrack", "soundtrack"),
                appleSong(22L, "Bohemian Rhapsody (Operatic Section / 2011 A Cappella Mix)", "Queen", "Deluxe", "deluxe"),
                appleSong(23L, "Bohemian Rhapsody", "Queen", "A Night At The Opera", "opera")));
        artwork("soundtrack", TestCovers.cover(21));
        artwork("deluxe", album);
        artwork("opera", album);

        resolve(key, SPOTIFY_LINK).andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.tracks[0].musicId").value("23"));
    }

    @Test
    void anotherVersionWithTheSameCoverIsOfferedNotPosted() throws Exception {
        String key = shareKey(phone());
        BufferedImage album = TestCovers.cover(30);
        spotifyTrack("Song", album);
        when(links.appleSearch("Song", "ua", 50)).thenReturn(List.of(
                appleSong(31L, "Song", "Someone Else", "Other", "other"),
                appleSong(32L, "Song (Acoustic)", "The Band", "Album", "album")));
        artwork("other", TestCovers.cover(31));
        artwork("album", album);

        JsonNode found = json(resolve(key, SPOTIFY_LINK).andExpect(status().isOk()));
        assertThat(found.get("matched").asBoolean()).isFalse();
        assertThat(found.get("tracks")).extracting(track -> track.get("musicId").asText()).containsExactly("32", "31");
    }

    @Test
    void songsWithAnotherCoverKeepApplesOrderWhateverTheirDistance() throws Exception {
        String key = shareKey(phone());
        spotifyTrack("Billie Jean", TestCovers.cover(40));
        when(links.appleSearch("Billie Jean", "ua", 50)).thenReturn(List.of(
                appleSong(41L, "Billie Jean", "Michael Jackson", "Thriller 40", "thriller"),
                appleSong(42L, "Billie Jean (Long Version)", "Michael Jackson", "Thriller 40", "thriller-long"),
                appleSong(43L, "Billie Jean", "A Cover Band", "Covers", "covers")));
        artwork("thriller", TestCovers.cover(41));
        artwork("thriller-long", TestCovers.cover(41));
        artwork("covers", TestCovers.cover(42));

        JsonNode found = json(resolve(key, SPOTIFY_LINK).andExpect(status().isOk()));
        assertThat(found.get("matched").asBoolean()).isFalse();
        // Apple's order, the same version first: the long one after the cover band's plain "Billie Jean".
        assertThat(found.get("tracks")).extracting(track -> track.get("musicId").asText()).containsExactly("41", "43", "42");
    }

    @Test
    void onlySongsFromAppleMusicOrSpotifyCanBeShared() throws Exception {
        String key = shareKey(phone());
        resolve(key, "https://open.spotify.com/album/4yP0hdKOZPNshxUOjY0cZj").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only songs can be shared"));
        mvc.perform(post("/share/resolve").header("X-Share-Key", key).header("Accept-Language", "uk")
                        .contentType(APPLICATION_JSON).content(linkJson("https://music.apple.com/ua/playlist/x/pl.1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Поділитися можна лише піснею"));
        resolve(key, "https://www.youtube.com/watch?v=4NRXx6U8ABQ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Share a song from Apple Music or Spotify"));
        when(links.appleSong(anyString(), anyString())).thenReturn(Optional.empty());
        resolve(key, APPLE_LINK).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Couldn't find this song in Apple Music"));
    }

    @Test
    void aShortLinkIsFollowedToTheSong() throws Exception {
        String key = shareKey(phone());
        when(links.redirectTarget("https://spotify.link/aBcD3fGh")).thenReturn(Optional.of(APPLE_LINK));
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));
        resolve(key, "https://spotify.link/aBcD3fGh").andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].musicId").value("1488408568"));
    }

    @Test
    void theShareKeyIsNeededAndOnlyItWorks() throws Exception {
        Phone me = phone();
        resolve(null, APPLE_LINK).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Open Lizma to sign in"));
        resolve("not-a-key", APPLE_LINK).andExpect(status().isUnauthorized());
        share(null, Map.of("musicId", "1", "musicProvider", "APPLE", "trackName", "Song", "artistName", "Artist"))
                .andExpect(status().isUnauthorized());

        String widgetKey = json(as(me.user(), post("/user/me/widget-key").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", me.refreshToken())))))
                .get("key").asText();
        resolve(widgetKey, APPLE_LINK).andExpect(status().isUnauthorized());
        mvc.perform(get("/widget/feed").header("X-Widget-Key", shareKey(me))).andExpect(status().isUnauthorized());
    }

    @Test
    void theKeyEndsWithTheSignIn() throws Exception {
        Phone me = phone();
        String key = shareKey(me);
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));
        resolve(key, APPLE_LINK).andExpect(status().isOk());

        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", me.refreshToken()))))
                .andExpect(status().is2xxSuccessful());
        resolve(key, APPLE_LINK).andExpect(status().isUnauthorized());

        Phone other = phone();
        shareKey(other);
        deleteAccount(other.user(), PASSWORD).andExpect(status().isOk());
        assertThat(extensionKeys.findAll()).noneMatch(k -> k.getUser().getId().equals(other.user().id()));
    }

    @Test
    void onlyTheExactSpotifyTrackPageCanComeWithAPost() throws Exception {
        String key = shareKey(phone());
        Map<String, Object> post = new HashMap<>(Map.of("musicId", "4", "musicProvider", "APPLE",
                "trackName", "Blinding Lights", "artistName", "The Weeknd"));
        for (String forged : List.of("https://open.spotify.com/track/" + SPOTIFY_ID + "?si=tracking",
                "https://evil.example/track/" + SPOTIFY_ID, "http://open.spotify.com/track/" + SPOTIFY_ID)) {
            post.put("spotifyUrl", forged);
            share(key, post).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This link isn't allowed in a post"));
        }
    }

    @Test
    void aLinkPastedIntoTheComposerIsFoundTheSameWay() throws Exception {
        User me = newUser();
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));
        as(me, post("/posts/resolve-link").contentType(APPLICATION_JSON).content(linkJson(APPLE_LINK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.tracks[0].trackName").value("Blinding Lights"));
        mvc.perform(post("/posts/resolve-link").contentType(APPLICATION_JSON).content(linkJson(APPLE_LINK)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookingUpLinksIsLimited() throws Exception {
        String key = shareKey(phone());
        when(links.appleSong("1488408568", "ua")).thenReturn(Optional.of(appleSong(1488408568L, "Blinding Lights",
                "The Weeknd", "After Hours", "after-hours")));
        for (int i = 0; i < 5; i++) {
            resolve(key, APPLE_LINK).andExpect(status().isOk());
        }
        resolve(key, APPLE_LINK).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("You've shared a lot of links. Please try again later."));
    }

    // MARK: helpers

    private Phone phone() throws Exception {
        User user = newUser();
        String refreshToken = json(signIn(user.name()).andExpect(status().isOk())).get("refreshToken").asText();
        return new Phone(user, refreshToken);
    }

    private String shareKey(Phone phone) throws Exception {
        return json(as(phone.user(), post("/user/me/share-key").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", phone.refreshToken()))))
                .andExpect(status().isOk())).get("key").asText();
    }

    private ResultActions resolve(String key, String link) throws Exception {
        var request = post("/share/resolve").contentType(APPLICATION_JSON).content(linkJson(link));
        if (key != null) {
            request.header("X-Share-Key", key);
        }
        return mvc.perform(request);
    }

    private ResultActions share(String key, Map<String, ?> body) throws Exception {
        var request = post("/share/posts").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (key != null) {
            request.header("X-Share-Key", key);
        }
        return mvc.perform(request);
    }

    private String linkJson(String link) throws Exception {
        return objectMapper.writeValueAsString(Map.of("url", link, "country", "UA"));
    }

    /** The post the share extension makes of a found song. */
    private static Map<String, Object> postOf(JsonNode track, String caption) {
        Map<String, Object> post = new HashMap<>();
        post.put("musicId", track.get("musicId").asText());
        post.put("musicProvider", "APPLE");
        post.put("trackName", track.get("trackName").asText());
        post.put("artistName", track.get("artistName").asText());
        post.put("albumCoverUrl", track.get("albumCoverUrl").asText());
        post.put("previewUrl", track.get("previewUrl").asText());
        post.put("appleMusicUrl", track.get("appleMusicUrl").asText());
        if (!track.get("spotifyUrl").isNull()) {
            post.put("spotifyUrl", track.get("spotifyUrl").asText());
        }
        if (caption != null) {
            post.put("caption", caption);
        }
        return post;
    }

    /** A song as Apple's lookup and search return it; {@code art} names its artwork. */
    private JsonNode appleSong(long id, String title, String artist, String album, String art) {
        return objectMapper.valueToTree(Map.of(
                "wrapperType", "track", "kind", "song", "trackId", id, "trackName", title, "artistName", artist,
                "collectionName", album,
                "artworkUrl100", artworkUrl(art),
                "previewUrl", "https://audio-ssl.itunes.apple.com/itunes-assets/" + art + ".m4a",
                "trackViewUrl", "https://music.apple.com/ua/album/" + art + "/1488408555?i=" + id + "&uo=4"));
    }

    private static String artworkUrl(String art) {
        return "https://is1-ssl.mzstatic.com/image/thumb/Music/" + art + "/100x100bb.jpg";
    }

    private void artwork(String art, BufferedImage cover) {
        when(links.cover(artworkUrl(art))).thenReturn(Optional.of(TestCovers.asAppleArtwork(cover)));
    }

    private void spotifyTrack(String title, BufferedImage cover) {
        when(links.spotifyTrack(SPOTIFY_ID)).thenReturn(Optional.of(objectMapper.valueToTree(Map.of(
                "title", title, "thumbnail_url", SPOTIFY_THUMBNAIL, "provider_name", "Spotify"))));
        when(links.cover(SPOTIFY_THUMBNAIL)).thenReturn(Optional.of(cover));
    }
}
