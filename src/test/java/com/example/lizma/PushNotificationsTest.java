package com.example.lizma;

import com.example.lizma.model.DeviceToken;
import com.example.lizma.repo.DeviceTokenRepository;
import com.example.lizma.service.push.PushGateway;
import com.example.lizma.service.push.PushMessage;
import com.example.lizma.service.push.PushNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.lizma.service.push.PushMessage.Type.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who gets a push notification about what: friend requests, reactions, comments, settings, phones, the daily reminder. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:push;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PushNotificationsTest.Recording.class, SyncBackgroundWork.class})
class PushNotificationsTest extends ApiTestSupport {

    @TestConfiguration
    static class Recording {
        @Bean
        @Primary
        RecordingGateway recordingGateway() {
            return new RecordingGateway();
        }
    }

    /** Remembers what would have been sent; tokens in {@link #gone} answer like a phone that deleted the app. */
    static class RecordingGateway implements PushGateway {
        record Sent(String token, DeviceToken.Environment environment, PushMessage message) {
        }

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        final Set<String> gone = ConcurrentHashMap.newKeySet();

        @Override
        public Result send(DeviceToken.Environment environment, String deviceToken, PushMessage message) {
            sent.add(new Sent(deviceToken, environment, message));
            return gone.contains(deviceToken) ? Result.INVALID_TOKEN : Result.SENT;
        }

        List<PushMessage> to(String token) {
            return sent.stream().filter(s -> s.token().equals(token)).map(Sent::message).toList();
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private DeviceTokenRepository devices;
    @Autowired
    private PushNotifier notifier;

    @BeforeEach
    void clear() {
        gateway.sent.clear();
    }

    @Test
    void aFriendRequestAndItsAcceptanceReachTheOtherPhone() throws Exception {
        User ann = newUser();
        User bob = newUser();
        String annPhone = register(ann, "SANDBOX");
        String bobPhone = register(bob, null);

        as(ann, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());

        assertThat(gateway.to(bobPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(FRIEND_REQUEST);
            assertThat(push.args()).containsExactly(ann.name());
            assertThat(push.route()).containsEntry("type", "friend_request").containsEntry("username", ann.name());
        });
        assertThat(gateway.sent).singleElement().extracting(RecordingGateway.Sent::environment)
                .isEqualTo(DeviceToken.Environment.PRODUCTION);

        long requestId = json(as(bob, get("/user/friend-requests/received"))).get(0).get("id").asLong();
        as(bob, put("/user/friend-requests/respond/" + requestId).param("status", "ACCEPTED")).andExpect(status().isOk());

        assertThat(gateway.to(annPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(NEW_FRIEND);
            assertThat(push.args()).containsExactly(bob.name());
        });
        assertThat(gateway.sent).filteredOn(s -> s.token().equals(annPhone))
                .extracting(RecordingGateway.Sent::environment).containsOnly(DeviceToken.Environment.SANDBOX);
    }

    @Test
    void aCancelledAndResentRequestIsNewsOnlyOnce() throws Exception {
        User ann = newUser();
        User bob = newUser();
        String bobPhone = register(bob, null);

        as(ann, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());
        long requestId = json(as(ann, get("/user/friend-requests/sent"))).get(0).get("id").asLong();
        as(ann, delete("/user/friend-requests/" + requestId)).andExpect(status().isOk());
        as(ann, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());

        assertThat(gateway.to(bobPhone)).hasSize(1);
    }

    @Test
    void reactionsCommentsAndRepliesGoToWhoTheyAreFor() throws Exception {
        User author = newUser();
        User fan = newUser();
        User other = newUser();
        makeFriends(author, fan);
        makeFriends(author, other);
        String authorPhone = register(author, null);
        String fanPhone = register(fan, null);
        String otherPhone = register(other, null);
        long postId = createPost(author, "listen");

        as(fan, post("/posts/" + postId + "/react").param("type", "🔥")).andExpect(status().isOk());
        assertThat(gateway.to(authorPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(REACTION);
            assertThat(push.args()).containsExactly(fan.name(), "🔥", "Song");
            assertThat(push.route()).containsEntry("type", "reaction").containsEntry("postId", postId);
            assertThat(push.threadId()).isEqualTo("post-" + postId);
        });
        // A different emoji, or the same one taken back and given again, isn't news.
        as(fan, post("/posts/" + postId + "/react").param("type", "😍")).andExpect(status().isOk());
        as(fan, post("/posts/" + postId + "/react").param("type", "😍")).andExpect(status().isOk());
        as(fan, post("/posts/" + postId + "/react").param("type", "😍")).andExpect(status().isOk());
        assertThat(gateway.to(authorPhone)).hasSize(1);

        long fanComment = commentId(as(fan, comment(postId, "Nice   one\nreally", null)));
        assertThat(gateway.to(authorPhone)).last().satisfies(push -> {
            assertThat(push.type()).isEqualTo(COMMENT);
            assertThat(push.args()).containsExactly(fan.name(), "Nice one really");
            assertThat(push.route()).containsEntry("type", "comment").containsEntry("postId", postId)
                    .containsEntry("commentId", fanComment);
        });

        // The author answers: the fan hears about the reply, the author nothing about their own words.
        int toAuthor = gateway.to(authorPhone).size();
        as(author, comment(postId, "Thanks!", fanComment)).andExpect(status().isOk());
        assertThat(gateway.to(fanPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(REPLY);
            assertThat(push.args()).containsExactly(author.name(), "Thanks!");
        });
        assertThat(gateway.to(authorPhone)).hasSize(toAuthor);

        // Someone else answers the fan: a reply for the fan, a comment for the post's author.
        as(other, comment(postId, "Agree", fanComment)).andExpect(status().isOk());
        assertThat(gateway.to(fanPhone)).hasSize(2).last().extracting(PushMessage::type).isEqualTo(REPLY);
        assertThat(gateway.to(authorPhone)).hasSize(toAuthor + 1).last().extracting(PushMessage::type).isEqualTo(COMMENT);
        assertThat(gateway.to(otherPhone)).isEmpty();
    }

    @Test
    void settingsAndBlocksKeepItQuiet() throws Exception {
        User author = newUser();
        User fan = newUser();
        User stranger = newUser();
        makeFriends(author, fan);
        makeFriends(author, stranger);
        String authorPhone = register(author, null);
        String fanPhone = register(fan, null);

        as(author, get("/user/me/notifications"))
                .andExpect(jsonPath("$.friendRequests").value(true))
                .andExpect(jsonPath("$.reactions").value(true))
                .andExpect(jsonPath("$.comments").value(true))
                .andExpect(jsonPath("$.dailyReminder").value(true));
        as(author, put("/user/me/notifications").contentType(APPLICATION_JSON).content("{\"reactions\":false}"))
                .andExpect(jsonPath("$.reactions").value(false))
                .andExpect(jsonPath("$.comments").value(true));

        long postId = createPost(author, "quiet");
        as(fan, post("/posts/" + postId + "/react").param("type", "❤️")).andExpect(status().isOk());
        assertThat(gateway.to(authorPhone)).isEmpty();

        // The fan blocked the stranger: the stranger's answer to the fan reaches only the post's author.
        long fanComment = commentId(as(fan, comment(postId, "first", null)));
        as(fan, post("/user/blocks/" + stranger.id())).andExpect(status().isOk());
        as(stranger, comment(postId, "hey", fanComment)).andExpect(status().isOk());
        assertThat(gateway.to(fanPhone)).isEmpty();
        assertThat(gateway.to(authorPhone)).extracting(PushMessage::type).containsExactly(COMMENT, COMMENT);
    }

    @Test
    void aPhoneBelongsToWhoeverSignedInLastAndLeavesWithTheAccount() throws Exception {
        User ann = newUser();
        User bob = newUser();
        String phone = token();
        register(ann, phone, null);
        register(bob, phone.toUpperCase(), null); // the same phone, now Bob's
        assertThat(devices.findByToken(phone)).get().extracting(d -> d.getUser().getId()).isEqualTo(bob.id());

        as(bob, put("/user/me/devices").contentType(APPLICATION_JSON).content("{\"token\":\"not-a-token\"}"))
                .andExpect(status().isBadRequest());

        // Ann signing out doesn't touch Bob's phone; Bob signing out does.
        as(ann, delete("/user/me/devices/" + phone)).andExpect(status().isOk());
        assertThat(devices.findByToken(phone)).isPresent();
        as(bob, delete("/user/me/devices/" + phone)).andExpect(status().isOk());
        assertThat(devices.findByToken(phone)).isEmpty();

        // Ten phones at most: the one registered longest ago goes.
        String first = register(bob, null);
        for (int i = 0; i < 10; i++) {
            register(bob, null);
        }
        assertThat(devices.findByUserIdOrderByUpdatedAtDesc(bob.id())).hasSize(10)
                .extracting(DeviceToken::getToken).doesNotContain(first);

        deleteAccount(bob, PASSWORD).andExpect(status().isOk());
        assertThat(devices.findByUserIdOrderByUpdatedAtDesc(bob.id())).isEmpty();
    }

    @Test
    void aPhoneThatDeletedTheAppIsForgotten() throws Exception {
        User ann = newUser();
        User bob = newUser();
        String bobPhone = register(bob, null);
        gateway.gone.add(bobPhone);

        as(ann, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());

        assertThat(gateway.to(bobPhone)).hasSize(1);
        assertThat(devices.findByToken(bobPhone)).isEmpty();
    }

    @Test
    void theDailyReminderGoesToThoseWhoHaventPostedToday() throws Exception {
        User posted = newUser();
        User quiet = newUser();
        User optedOut = newUser();
        String postedPhone = register(posted, null);
        String quietPhone = register(quiet, null);
        String optedOutPhone = register(optedOut, null);
        as(optedOut, put("/user/me/notifications").contentType(APPLICATION_JSON).content("{\"dailyReminder\":false}"))
                .andExpect(status().isOk());
        createPost(posted, "today's song");

        notifier.remindEveryoneWhoHasntPostedSince(Instant.now().minusSeconds(60));

        assertThat(gateway.to(quietPhone)).singleElement().satisfies(push -> {
            assertThat(push.type()).isEqualTo(DAILY_REMINDER);
            assertThat(push.args()).isEmpty();
            assertThat(push.collapseId()).isEqualTo("daily-reminder");
            assertThat(push.expiresAt()).isAfter(Instant.now());
            assertThat(push.route()).isEqualTo(Map.of("type", "daily_reminder"));
        });
        assertThat(gateway.to(postedPhone)).isEmpty();
        assertThat(gateway.to(optedOutPhone)).isEmpty();
    }

    @Test
    void signingOutForgetsThePhoneButOnlyYourOwn() throws Exception {
        User ann = newUser();
        User bob = newUser();
        String annPhone = register(ann, null);
        String annSession = json(signIn(ann.name()).andExpect(status().isOk())).get("refreshToken").asText();
        String bobSession = json(signIn(bob.name()).andExpect(status().isOk())).get("refreshToken").asText();

        logout(bobSession, annPhone); // Bob's session can't switch off Ann's phone
        assertThat(devices.findByToken(annPhone)).isPresent();

        logout(annSession, annPhone.toUpperCase());
        assertThat(devices.findByToken(annPhone)).isEmpty();
    }

    private void logout(String refreshToken, String deviceToken) throws Exception {
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(
                Map.of("refreshToken", refreshToken, "deviceToken", deviceToken)))).andExpect(status().isOk());
    }

    private String register(User user, String environment) throws Exception {
        return register(user, token(), environment);
    }

    private String register(User user, String token, String environment) throws Exception {
        String body = environment == null
                ? objectMapper.writeValueAsString(Map.of("token", token))
                : objectMapper.writeValueAsString(Map.of("token", token, "environment", environment));
        as(user, put("/user/me/devices").contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
        return token.toLowerCase();
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
