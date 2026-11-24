package com.example.lizma;

import com.example.lizma.model.DeviceToken;
import com.example.lizma.repo.WidgetKeyRepository;
import com.example.lizma.service.push.PushMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The home screen widget: its key, what it shows, when it stops working, and the push that refreshes it. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:widgets;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PushNotificationsTest.Recording.class, SyncBackgroundWork.class})
class WidgetIntegrationTest extends ApiTestSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private PushNotificationsTest.RecordingGateway gateway;
    @Autowired
    private WidgetKeyRepository widgetKeys;

    /** A user with a session of their own: the refresh token a widget key is tied to. */
    private record Phone(User user, String refreshToken) {
    }

    @BeforeEach
    void clear() {
        gateway.sent.clear();
    }

    @Test
    void theWidgetShowsFriendsLatestSongsNewestFirst() throws Exception {
        Phone me = phone();
        User liza = newUser();
        User bob = newUser();
        makeFriends(me.user(), liza);
        makeFriends(me.user(), bob);
        as(liza, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"Liza\"}"))
                .andExpect(status().isOk());
        createPost(liza, "first");
        createPost(bob, "second");
        createPost(me.user(), "my own one isn't news");
        for (int i = 0; i < 4; i++) {
            createPost(liza, "more " + i);
        }
        long newest = createPost(bob, "newest");

        String key = widgetKey(me);
        JsonNode posts = json(feed(key).andExpect(status().isOk())).get("posts");

        assertThat(posts).hasSize(4);
        assertThat(posts.get(0).get("id").asLong()).isEqualTo(newest);
        assertThat(posts.get(0).get("authorName").asText()).isEqualTo(bob.name());
        assertThat(posts.get(1).get("authorName").asText()).isEqualTo("Liza");
        assertThat(posts.get(1).get("authorUsername").asText()).isEqualTo(liza.name());
        assertThat(posts.get(0).get("trackName").asText()).isEqualTo("Song");
        assertThat(posts.get(0).has("createdAt")).isTrue();
        posts.forEach(post -> assertThat(post.get("authorUsername").asText()).isNotEqualTo(me.user().name()));
    }

    @Test
    void withoutAValidKeyTheWidgetGetsNothing() throws Exception {
        Phone me = phone();
        feed(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Open Lizma to sign in"));
        feed("made-up").andExpect(status().isUnauthorized());

        // The key opens the widget feed and nothing else.
        String key = widgetKey(me);
        mvc.perform(get("/posts/feed").header("Authorization", "Bearer " + key)).andExpect(status().isUnauthorized());

        // A refresh token of someone else's sign-in doesn't make a key.
        Phone other = phone();
        as(me.user(), post("/user/me/widget-key").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", other.refreshToken()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sign in again to set up the widget"));
    }

    @Test
    void theKeyEndsWithTheSignInItBelongsTo() throws Exception {
        Phone me = phone();
        String key = widgetKey(me);
        feed(key).andExpect(status().isOk());

        // Asking again for the same sign-in replaces the key.
        String newKey = widgetKey(me);
        feed(key).andExpect(status().isUnauthorized());
        feed(newKey).andExpect(status().isOk());

        // The session renews (the refresh token rotates): the widget keeps working.
        JsonNode renewed = json(mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", me.refreshToken())))).andExpect(status().isOk()));
        feed(newKey).andExpect(status().isOk());

        // Signing out on that phone ends it.
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", renewed.get("refreshToken").asText()))))
                .andExpect(status().isOk());
        feed(newKey).andExpect(status().isUnauthorized());
    }

    @Test
    void blockedFriendsAndSuspendedOrDeletedAccountsLeaveTheWidget() throws Exception {
        Phone me = phone();
        User friend = newUser();
        makeFriends(me.user(), friend);
        createPost(friend, "hello");
        String key = widgetKey(me);
        assertThat(json(feed(key)).get("posts")).hasSize(1);

        as(me.user(), post("/user/blocks/" + friend.id())).andExpect(status().isOk());
        assertThat(json(feed(key)).get("posts")).isEmpty();

        User moderator = newUser();
        makeModerator(moderator);
        as(moderator, post("/admin/users/" + me.user().name() + "/ban")).andExpect(status().isOk());
        feed(key).andExpect(status().isUnauthorized());

        Phone other = phone();
        widgetKey(other);
        deleteAccount(other.user(), PASSWORD).andExpect(status().isOk());
        assertThat(widgetKeys.findAll()).noneMatch(k -> k.getUser().getId().equals(other.user().id()));
    }

    @Test
    void aFriendsNewSongWakesThePhonesWhoseWidgetIsShowing() throws Exception {
        Phone withWidget = phone();
        Phone withoutWidget = phone();
        User author = newUser();
        makeFriends(author, withWidget.user());
        makeFriends(author, withoutWidget.user());
        String widgetPhone = registerPhone(withWidget.user());
        String otherPhone = registerPhone(withoutWidget.user());
        String authorPhone = registerPhone(author);
        feed(widgetKey(withWidget)).andExpect(status().isOk()); // the widget is on a home screen
        widgetKey(withoutWidget);                                 // set up, never shown

        createPost(author, "listen to this");

        assertThat(gateway.to(widgetPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(PushMessage.Type.WIDGET_REFRESH);
            assertThat(push.route()).isEqualTo(Map.of("type", "widget_refresh"));
        });
        assertThat(gateway.to(otherPhone)).isEmpty();
        assertThat(gateway.to(authorPhone)).isEmpty();
        assertThat(gateway.sent).filteredOn(s -> s.token().equals(widgetPhone))
                .extracting(PushNotificationsTest.RecordingGateway.Sent::environment)
                .containsOnly(DeviceToken.Environment.PRODUCTION);

        // Another song right after: the phone isn't woken again so soon.
        createPost(author, "and this");
        assertThat(gateway.to(widgetPhone)).hasSize(1);
    }

    private Phone phone() throws Exception {
        User user = newUser();
        String refreshToken = json(signIn(user.name()).andExpect(status().isOk())).get("refreshToken").asText();
        return new Phone(user, refreshToken);
    }

    private String widgetKey(Phone phone) throws Exception {
        return json(as(phone.user(), post("/user/me/widget-key").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", phone.refreshToken()))))
                .andExpect(status().isOk())).get("key").asText();
    }

    private ResultActions feed(String key) throws Exception {
        var request = get("/widget/feed");
        if (key != null) {
            request.header("X-Widget-Key", key);
        }
        return mvc.perform(request);
    }

    private String registerPhone(User user) throws Exception {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        as(user, put("/user/me/devices").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token)))).andExpect(status().isOk());
        return token;
    }
}
