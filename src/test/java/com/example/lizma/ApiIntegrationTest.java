package com.example.lizma;

import com.example.lizma.model.FriendInvite;
import com.example.lizma.repo.FriendInviteRepository;
import com.example.lizma.repo.UserRepository;
import com.example.lizma.service.ContentCleanupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the REST API (auth, friends, invites, posts, music) on an in-memory H2 database.
 * Every test creates its own users, so tests don't depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ContentCleanupService cleanupService;
    @Autowired
    private FriendInviteRepository inviteRepository;

    private record User(long id, String name, String token) {
    }

    // ---------------------------------------------------------------- auth

    @Test
    void signUpValidatesUsernameAndPassword() throws Exception {
        signUp("bad name", PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username can contain only letters, digits, '.', '_' and '-'"));
        signUp("weak_user", "abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password length must be between 8 and 255 characters"));
        mvc.perform(post("/auth/sign-up").contentType(APPLICATION_JSON).content("{\"username\":\"no_password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUpRejectsTakenUsername() throws Exception {
        User user = newUser();
        signUp(user.name(), PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    void signInChecksCredentials() throws Exception {
        User user = newUser();
        signIn(user.name(), "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect password"));
        signIn("nobody_here", PASSWORD).andExpect(status().isNotFound());
        signIn(user.name(), PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void protectedEndpointsAnswer401WithoutValidToken() throws Exception {
        mvc.perform(get("/user/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(get("/user/friends").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expired, please sign in again"));
    }

    @Test
    void tokenOfDeletedUserIsRejectedButDoesNotBreakSignIn() throws Exception {
        User ghost = newUser();
        User other = newUser();
        cleanupService.deleteUser(ghost.id());

        as(ghost, get("/user/friends")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/sign-in").header("Authorization", "Bearer " + ghost.token())
                        .contentType(APPLICATION_JSON).content(credentials(other.name(), PASSWORD)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- generic errors

    @Test
    void clientMistakesAreReportedAs4xx() throws Exception {
        User user = newUser();
        as(user, get("/does/not/exist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
        as(user, post("/posts").contentType(APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        as(user, post("/posts").contentType(APPLICATION_JSON).content(
                "{\"musicId\":\"x\",\"musicProvider\":\"FOO\",\"trackName\":\"t\",\"artistName\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for field 'musicProvider'"));
        as(user, post("/posts").contentType(APPLICATION_JSON).content(postJson("x".repeat(2000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Caption must be at most 1024 characters"));
        as(user, get("/posts/feed").param("page", "abc")).andExpect(status().isBadRequest());
        as(user, get("/posts/feed").param("page", "-1").param("size", "1000")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- friend requests

    @Test
    void friendRequestFlow() throws Exception {
        User alice = newUser();
        User bob = newUser();
        User carl = newUser();

        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk())
                .andExpect(content().string("Request sent."));
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isConflict());
        as(alice, post("/user/friend-requests/send/" + alice.id())).andExpect(status().isBadRequest());
        as(alice, post("/user/friend-requests/send/999999")).andExpect(status().isNotFound());
        as(alice, get("/user/friend-requests/sent")).andExpect(jsonPath("$", hasSize(1)));

        long requestId = json(as(bob, get("/user/friend-requests/received"))).get(0).get("id").asLong();
        respond(bob, requestId, "FOO").andExpect(status().isBadRequest());
        respond(bob, requestId, "PENDING").andExpect(status().isBadRequest());
        respond(carl, requestId, "ACCEPTED").andExpect(status().isForbidden());
        respond(bob, requestId, "ACCEPTED").andExpect(status().isOk());
        respond(bob, requestId, "ACCEPTED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This friend request has already been processed"));

        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].friendUsername").value(bob.name()));
        as(bob, get("/user/friends")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].friendUsername").value(alice.name()));
        as(alice, get("/user/friend-requests/sent")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You are already friends"));
    }

    @Test
    void mutualRequestsBecomeOneFriendship() throws Exception {
        User alice = newUser();
        User bob = newUser();

        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(content().string("Request sent."));
        as(bob, post("/user/friend-requests/send/" + alice.id())).andExpect(content().string("You are now friends!"));

        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(1)));
        as(bob, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void declinedRequestCanBeSentAgain() throws Exception {
        User alice = newUser();
        User bob = newUser();

        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());
        long requestId = json(as(bob, get("/user/friend-requests/received"))).get(0).get("id").asLong();
        respond(bob, requestId, "DECLINED").andExpect(status().isOk());
        as(alice, get("/user/friend-requests/sent")).andExpect(jsonPath("$", hasSize(0)));

        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(content().string("Request sent."));
        as(bob, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void userSearchFindsOthersButNotYourself() throws Exception {
        User alice = newUser();
        User bob = newUser();

        // search returns at most 20 users and other tests create many "user_N", so query exact names
        assertThat(json(as(alice, get("/user/search").param("query", bob.name()))).findValuesAsText("username"))
                .contains(bob.name());
        assertThat(json(as(alice, get("/user/search").param("query", alice.name()))).findValuesAsText("username"))
                .doesNotContain(alice.name());
        as(alice, get("/user/lookup").param("username", "nobody_here")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- invite links

    @Test
    void inviteLinksAreSecretSingleUseAndExpire() throws Exception {
        User alice = newUser();
        User bob = newUser();
        User carl = newUser();

        JsonNode invite = createInvite(alice);
        String token = invite.get("token").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(invite.get("url").asText()).isEqualTo("http://localhost:8080/i/" + token);
        assertThat(Instant.parse(invite.get("expiresAt").asText())).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(createInvite(alice).get("token").asText()).isNotEqualTo(token);

        // the public page opens the app; no sign-in needed
        mvc.perform(get("/i/" + token)).andExpect(status().isOk())
                .andExpect(content().string(containsString("lizma://i/" + token)))
                .andExpect(content().string(containsString(alice.name() + " invited you")));
        mvc.perform(get("/i/not-a-real-token")).andExpect(status().isNotFound());

        acceptInvite(alice, token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This is your own invite link"));
        acceptInvite(bob, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Friendship established"));
        acceptInvite(bob, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You are already friends"));
        acceptInvite(carl, token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invite link has already been used"));
        acceptInvite(carl, "not-a-real-token").andExpect(status().isNotFound());
        mvc.perform(get("/i/" + token)).andExpect(status().isNotFound());
        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(1)));

        String expired = createInvite(alice).get("token").asText();
        FriendInvite stored = inviteRepository.findByToken(expired).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        inviteRepository.save(stored);
        acceptInvite(carl, expired).andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This invite link has expired"));
        mvc.perform(get("/i/" + expired)).andExpect(status().isNotFound());
    }

    @Test
    void inviteIsNotSpentOnExistingFriendsAndResolvesPendingRequests() throws Exception {
        User alice = newUser();
        User bob = newUser();
        User carl = newUser();
        makeFriends(alice, bob);

        String token = createInvite(alice).get("token").asText();
        acceptInvite(bob, token).andExpect(jsonPath("$.message").value("You are already friends"));

        // still usable by someone new; their pending request becomes accepted
        as(carl, post("/user/friend-requests/send/" + alice.id())).andExpect(status().isOk());
        acceptInvite(carl, token).andExpect(jsonPath("$.message").value("Friendship established"));
        as(alice, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void usernameBasedInvitesNoLongerExist() throws Exception {
        User alice = newUser();
        User bob = newUser();

        as(bob, post("/user/friend-invites/accept-by-username/" + alice.name())).andExpect(status().isNotFound());
        as(bob, post("/user/friend-invites/vanity-link")).andExpect(status().isNotFound());
        // The path now belongs to DELETE /user/friend-requests/{id} (cancel), so a POST is refused.
        as(alice, post("/user/friend-requests/generate-link")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/" + alice.name())).andExpect(status().isUnauthorized());
        as(bob, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------------------------------------------------------------- posts

    @Test
    void postsAreVisibleOnlyToTheAuthorAndFriends() throws Exception {
        User author = newUser();
        User friend = newUser();
        User stranger = newUser();
        makeFriends(author, friend);
        long postId = createPost(author);

        as(friend, get("/posts/feed")).andExpect(jsonPath("$[0].id").value(postId));
        as(stranger, get("/posts/feed")).andExpect(jsonPath("$", hasSize(0)));

        as(stranger, get("/posts/" + postId + "/comments")).andExpect(status().isNotFound());
        as(stranger, post("/posts/" + postId + "/react").param("type", "🔥")).andExpect(status().isNotFound());
        as(stranger, post("/posts/" + postId + "/comments").contentType(APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
        as(friend, get("/posts/999999/comments")).andExpect(status().isNotFound());
    }

    @Test
    void reactionsRules() throws Exception {
        User author = newUser();
        User friend = newUser();
        makeFriends(author, friend);
        long postId = createPost(author);
        String react = "/posts/" + postId + "/react";

        as(author, post(react).param("type", "🔥")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't react to your own post"));
        as(friend, post(react).param("type", "y".repeat(20))).andExpect(status().isBadRequest());
        as(friend, post(react).param("type", "  ")).andExpect(status().isBadRequest());

        as(friend, post(react).param("type", "🔥")).andExpect(status().isOk());
        as(friend, get("/posts/feed")).andExpect(jsonPath("$[0].currentUserReaction").value("🔥"));
        as(author, get("/posts/" + postId + "/reactions")).andExpect(jsonPath("$[0].type").value("🔥"));
        as(friend, get("/posts/" + postId + "/reactions")).andExpect(status().isForbidden());

        as(friend, post(react).param("type", "🔥")).andExpect(status().isOk()); // same emoji = remove
        as(author, get("/posts/" + postId + "/reactions")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void repliesStayOneLevelDeepAndCommentLikesToggle() throws Exception {
        User author = newUser();
        User friend = newUser();
        makeFriends(author, friend);
        long postId = createPost(author);
        String comments = "/posts/" + postId + "/comments";

        long top = json(as(friend, post(comments).contentType(APPLICATION_JSON).content("{\"content\":\"top\"}"))).get("id").asLong();
        long reply = json(as(author, post(comments).contentType(APPLICATION_JSON)
                .content("{\"content\":\"reply\",\"parentId\":" + top + "}"))).get("id").asLong();
        as(friend, post(comments).contentType(APPLICATION_JSON)
                .content("{\"content\":\"reply to reply\",\"parentId\":" + reply + "}"))
                .andExpect(jsonPath("$.parentId").value(top));
        as(friend, post(comments).contentType(APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        as(author, get(comments))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].replies", hasSize(2)))
                .andExpect(jsonPath("$[0].replies[1].content").value("reply to reply"));
        as(author, get("/posts/feed")).andExpect(jsonPath("$[0].commentsCount").value(3));

        as(author, post("/posts/999999/comments/" + top + "/like")).andExpect(status().isNotFound());
        as(author, post(comments + "/" + top + "/like")).andExpect(status().isOk());
        as(author, get(comments))
                .andExpect(jsonPath("$[0].likesCount").value(1))
                .andExpect(jsonPath("$[0].likedByCurrentUser").value(true));
        as(author, post(comments + "/" + top + "/like")).andExpect(status().isOk());
        as(author, get(comments)).andExpect(jsonPath("$[0].likesCount").value(0));
    }

    // ---------------------------------------------------------------- music

    @Test
    void spotifyProblemsAreClientErrors() throws Exception {
        User user = newUser();
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(false));
        as(user, get("/music/search").param("query", "abba")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Spotify account is not connected"));
        as(user, get("/music/search").param("query", " ")).andExpect(status().isBadRequest());
        as(user, post("/music/spotify/exchange").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private User newUser() throws Exception {
        String name = "user_" + SEQ.incrementAndGet();
        String token = json(signUp(name, PASSWORD).andExpect(status().isOk())).get("token").asText();
        long id = json(mvc.perform(get("/user/lookup").param("username", name)
                .header("Authorization", "Bearer " + token))).get("id").asLong();
        return new User(id, name, token);
    }

    private ResultActions signUp(String username, String password) throws Exception {
        return mvc.perform(post("/auth/sign-up").contentType(APPLICATION_JSON).content(credentials(username, password)));
    }

    private ResultActions signIn(String username, String password) throws Exception {
        return mvc.perform(post("/auth/sign-in").contentType(APPLICATION_JSON).content(credentials(username, password)));
    }

    private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token()));
    }

    private ResultActions respond(User user, long requestId, String status) throws Exception {
        return as(user, put("/user/friend-requests/respond/" + requestId).param("status", status));
    }

    private JsonNode createInvite(User inviter) throws Exception {
        return json(as(inviter, post("/user/friend-invites")).andExpect(status().isOk()));
    }

    private ResultActions acceptInvite(User user, String token) throws Exception {
        return as(user, post("/user/friend-invites/" + token + "/accept"));
    }

    private void makeFriends(User inviter, User invitee) throws Exception {
        acceptInvite(invitee, createInvite(inviter).get("token").asText())
                .andExpect(jsonPath("$.message").value("Friendship established"));
    }

    private long createPost(User author) throws Exception {
        return json(as(author, post("/posts").contentType(APPLICATION_JSON).content(postJson("my song")))
                .andExpect(status().isOk())).get("id").asLong();
    }

    /** Works for sign-in too: the extra acceptTerms field is ignored there. */
    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password, "acceptTerms", true));
    }

    private String postJson(String caption) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "musicId", "track-1", "musicProvider", "SPOTIFY",
                "trackName", "Song", "artistName", "Artist", "caption", caption));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
