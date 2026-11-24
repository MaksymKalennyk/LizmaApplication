package com.example.lizma;

import com.example.lizma.service.ContentFilter;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What App Store review asks of a social app: terms at sign-up, a content filter, reporting,
 * blocking, moderation (guideline 1.2) and in-app account deletion (guideline 5.1.1(v)).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SafetyIntegrationTest extends ApiTestSupport {

    // ---------------------------------------------------------------- sign-up & filter

    @Test
    void signUpRequiresAcceptingTheTerms() throws Exception {
        String name = nextName();
        signUp(Map.of("username", name, "password", PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You need to accept the Terms of Use and Privacy Policy"));
        signUp(Map.of("username", name, "password", PASSWORD, "acceptTerms", false)).andExpect(status().isBadRequest());
        signUp(Map.of("username", name, "password", PASSWORD, "acceptTerms", true)).andExpect(status().isOk());
        assertThat(userRepository.findByUsername(name).orElseThrow().getTermsAcceptedAt()).isNotNull();
    }

    @Test
    void offensiveWordsAreRejectedButInnocentOnesPass() throws Exception {
        signUp(Map.of("username", "fuck_" + SEQ.incrementAndGet(), "password", PASSWORD, "acceptTerms", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ContentFilter.MESSAGE));

        User author = newUser();
        User friend = newUser();
        makeFriends(author, friend);
        as(author, post("/posts").contentType(APPLICATION_JSON).content(postJson("Sh1t happens"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ContentFilter.MESSAGE));
        long postId = createPost(author, "Shiitake soup, a classic, and fire retardant");

        as(friend, post("/posts/" + postId + "/comments").contentType(APPLICATION_JSON).content("{\"content\":\"ты мудак\"}"))
                .andExpect(status().isBadRequest());
        as(friend, post("/posts/" + postId + "/comments").contentType(APPLICATION_JSON).content("{\"content\":\"Classic track!\"}"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- account deletion

    @Test
    void deletingTheAccountNeedsThePasswordAndRemovesAllOfItsData() throws Exception {
        User leaving = newUser();
        User friend = newUser();
        User other = newUser();
        makeFriends(leaving, friend);

        long leavingPost = createPost(leaving, "bye");
        long friendPost = createPost(friend, "hello");
        long friendComment = commentId(as(friend, comment(leavingPost, "nice", null)));
        long leavingComment = commentId(as(leaving, comment(friendPost, "great", null)));
        as(friend, comment(friendPost, "reply to the leaving user", leavingComment)).andExpect(status().isOk());
        as(leaving, post("/posts/" + friendPost + "/comments/" + commentId(as(friend, comment(friendPost, "mine", null))) + "/like"))
                .andExpect(status().isOk());
        as(leaving, post("/posts/" + friendPost + "/react").param("type", "🔥")).andExpect(status().isOk());
        as(friend, post("/posts/" + leavingPost + "/react").param("type", "🎵")).andExpect(status().isOk());
        as(leaving, post("/user/friend-requests/send/" + other.id())).andExpect(status().isOk());
        as(leaving, post("/user/friend-invites")).andExpect(status().isOk());
        as(other, post("/user/blocks/" + leaving.id())).andExpect(status().isOk());
        as(leaving, post("/reports").contentType(APPLICATION_JSON)
                .content("{\"targetType\":\"POST\",\"targetId\":" + friendPost + ",\"reason\":\"SPAM\"}")).andExpect(status().isOk());
        assertThat(friendComment).isPositive();

        deleteAccount(leaving, "wrong-password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect password"));
        deleteAccount(leaving, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted"));

        as(leaving, get("/user/friends")).andExpect(status().isUnauthorized());
        assertThat(userRepository.findByUsername(leaving.name())).isEmpty();
        as(friend, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(friend, get("/posts/feed")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(friendPost))
                .andExpect(jsonPath("$[0].reactionsCount").isEmpty());
        JsonNode comments = json(as(friend, get("/posts/" + friendPost + "/comments")));
        assertThat(comments.findValuesAsText("content")).containsExactly("mine");
        assertThat(comments.get(0).get("likesCount").asInt()).isZero();
        as(other, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(0)));
        as(other, get("/user/blocks")).andExpect(jsonPath("$", hasSize(0)));

        // the username is free again
        signUp(Map.of("username", leaving.name(), "password", PASSWORD, "acceptTerms", true)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- blocking

    @Test
    void blockingCutsEveryConnectionUntilUnblocked() throws Exception {
        User alice = newUser();
        User bob = newUser();
        User carol = newUser();
        makeFriends(alice, bob);
        makeFriends(carol, bob);
        long bobPost = createPost(bob, "my track");
        as(alice, comment(bobPost, "from alice", null)).andExpect(status().isOk());

        as(bob, post("/user/blocks/" + alice.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User blocked"));
        as(bob, post("/user/blocks/" + alice.id())).andExpect(status().isOk()); // idempotent
        as(bob, get("/user/blocks")).andExpect(jsonPath("$[0].username").value(alice.name()));

        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can't connect with this user"));
        as(bob, post("/user/friend-requests/send/" + alice.id())).andExpect(status().isForbidden());
        String bobsInvite = json(as(bob, post("/user/friend-invites"))).get("token").asText();
        as(alice, post("/user/friend-invites/" + bobsInvite + "/accept")).andExpect(status().isForbidden());
        as(alice, get("/user/lookup").param("username", bob.name())).andExpect(status().isNotFound());
        assertThat(json(as(alice, get("/user/search").param("query", bob.name()))).findValuesAsText("username"))
                .doesNotContain(bob.name());

        // bob no longer sees alice's comment; carol (not involved in the block) still does
        assertThat(json(as(bob, get("/posts/" + bobPost + "/comments"))).findValuesAsText("content")).doesNotContain("from alice");
        assertThat(json(as(carol, get("/posts/" + bobPost + "/comments"))).findValuesAsText("content")).contains("from alice");

        as(bob, delete("/user/blocks/" + alice.id())).andExpect(status().isOk());
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(content().string("Request sent."));

        as(bob, post("/user/blocks/" + bob.id())).andExpect(status().isBadRequest());
        as(bob, post("/user/blocks/999999")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- reports & moderation

    @Test
    void reportsReachModeratorsWhoCanRemoveContentAndSuspendUsers() throws Exception {
        User reporter = newUser();
        User author = newUser();
        User stranger = newUser();
        User moderator = newUser();
        makeFriends(reporter, author);
        long postId = createPost(author, "suspicious caption");
        long ownComment = commentId(as(reporter, comment(postId, "my own comment", null)));

        report(reporter, "POST", postId, "SPAM").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("24 hours")));
        report(reporter, "POST", postId, "SPAM").andExpect(status().isOk()); // no duplicate
        report(reporter, "COMMENT", ownComment, "OTHER").andExpect(status().isBadRequest());
        report(reporter, "USER", reporter.id(), "OTHER").andExpect(status().isBadRequest());
        report(stranger, "POST", postId, "HATE").andExpect(status().isNotFound());
        report(reporter, "COMMENT", 999999, "SPAM").andExpect(status().isNotFound());
        report(reporter, "USER", author.id(), "HARASSMENT").andExpect(status().isOk());

        as(reporter, get("/admin/reports")).andExpect(status().isForbidden());
        makeModerator(moderator);
        JsonNode open = json(as(moderator, get("/admin/reports")));
        JsonNode postReport = findReport(open, "POST", postId);
        assertThat(postReport.get("reporterUsername").asText()).isEqualTo(reporter.name());
        assertThat(postReport.get("targetUsername").asText()).isEqualTo(author.name());
        assertThat(postReport.get("targetPreview").asText()).contains("suspicious caption");
        assertThat(reportsOn(open, "POST", postId)).hasSize(1); // reporting it twice left one report

        resolve(moderator, postReport.get("id").asLong(), "REMOVE_CONTENT").andExpect(status().isOk());
        resolve(moderator, postReport.get("id").asLong(), "DISMISS").andExpect(status().isConflict());
        as(reporter, get("/posts/" + postId + "/comments")).andExpect(status().isNotFound());
        as(reporter, get("/posts/feed")).andExpect(jsonPath("$", hasSize(0)));

        long userReport = findReport(json(as(moderator, get("/admin/reports"))), "USER", author.id()).get("id").asLong();
        resolve(moderator, userReport, "BAN_USER").andExpect(status().isOk());
        signIn(author.name()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been suspended"));
        as(author, get("/user/friends")).andExpect(status().isUnauthorized());
        JsonNode resolved = json(as(moderator, get("/admin/reports").param("status", "RESOLVED")));
        assertThat(reportsAbout(resolved, author.name())).hasSize(2);
    }

    @Test
    void suspendingAUserRemovesEverythingTheyPostedAndHidesThem() throws Exception {
        User offender = newUser();
        User friend = newUser();
        User other = newUser();
        User moderator = newUser();
        makeModerator(moderator);
        makeFriends(offender, friend);

        long reported = createPost(offender, "reported post");
        long another = createPost(offender, "another post by the same user");
        long friendPost = createPost(friend, "friend's post");
        long offenderComment = commentId(as(offender, comment(friendPost, "comment by the offender", null)));
        as(friend, comment(friendPost, "reply to the offender", offenderComment)).andExpect(status().isOk());
        long friendComment = commentId(as(friend, comment(friendPost, "friend's own comment", null)));
        as(offender, post("/posts/" + friendPost + "/comments/" + friendComment + "/like")).andExpect(status().isOk());
        as(offender, post("/posts/" + friendPost + "/react").param("type", "🔥")).andExpect(status().isOk());
        as(other, post("/user/friend-requests/send/" + offender.id())).andExpect(status().isOk());
        String invite = json(as(offender, post("/user/friend-invites"))).get("token").asText();
        as(other, post("/user/blocks/" + offender.id())).andExpect(status().isOk()); // also drops other's request
        as(offender, post("/music/apple/connect").contentType(APPLICATION_JSON)
                .content("{\"userToken\":\"token\",\"storefront\":\"us\"}")).andExpect(status().isOk());
        report(friend, "POST", reported, "HATE").andExpect(status().isOk());
        report(friend, "POST", another, "SPAM").andExpect(status().isOk());       // a second open report about them
        report(offender, "POST", friendPost, "OTHER").andExpect(status().isOk()); // the one they filed stays open

        long reportId = findReport(json(as(moderator, get("/admin/reports"))), "POST", reported).get("id").asLong();
        resolve(moderator, reportId, "BAN_USER").andExpect(status().isOk());

        // the account is locked out
        signIn(offender.name()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been suspended"));
        as(offender, get("/user/friends")).andExpect(status().isUnauthorized());

        // nothing of theirs is left for the others
        as(friend, get("/posts/feed")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(friendPost))
                .andExpect(jsonPath("$[0].reactionsCount").isEmpty());
        JsonNode comments = json(as(friend, get("/posts/" + friendPost + "/comments")));
        assertThat(comments.findValuesAsText("content")).containsExactly("friend's own comment");
        assertThat(comments.get(0).get("likesCount").asInt()).isZero();
        as(friend, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(friend, post("/user/friend-invites/" + invite + "/accept")).andExpect(status().isNotFound());
        as(friend, get("/music/status")).andExpect(status().isOk());

        // they can't be found or contacted, and the name stays taken
        as(friend, get("/user/lookup").param("username", offender.name())).andExpect(status().isNotFound());
        assertThat(json(as(friend, get("/user/search").param("query", offender.name()))).findValuesAsText("username")).isEmpty();
        as(friend, post("/user/friend-requests/send/" + offender.id())).andExpect(status().isNotFound());
        signUp(Map.of("username", offender.name(), "password", PASSWORD, "acceptTerms", true)).andExpect(status().isConflict());
        // other people's blocks against them are theirs to keep
        as(other, get("/user/blocks")).andExpect(jsonPath("$[0].username").value(offender.name()));

        // every report about them is closed; the report they filed is still waiting for a moderator
        JsonNode open = json(as(moderator, get("/admin/reports")));
        assertThat(reportsAbout(open, offender.name())).isEmpty();
        assertThat(open.findValuesAsText("reporterUsername")).contains(offender.name());
        assertThat(reportsAbout(json(as(moderator, get("/admin/reports").param("status", "RESOLVED"))), offender.name())).hasSize(2);
    }

    @Test
    void moderatorsCanSuspendReinstateAndDeleteAccountsDirectly() throws Exception {
        User user = newUser();
        User friend = newUser();
        User moderator = newUser();
        makeFriends(user, friend);
        createPost(user, "a post");

        as(friend, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isForbidden());
        makeModerator(moderator);
        as(moderator, post("/admin/users/" + moderator.name() + "/ban")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A moderator's account can't be suspended"));
        as(moderator, post("/admin/users/nobody_here/ban")).andExpect(status().isNotFound());

        as(moderator, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User suspended"));
        as(moderator, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isOk()); // harmless to repeat
        signIn(user.name()).andExpect(status().isForbidden());
        as(friend, get("/posts/feed")).andExpect(jsonPath("$", hasSize(0)));
        as(friend, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));

        // reinstated: they can sign in again, but what was removed stays removed
        as(moderator, post("/admin/users/" + user.name() + "/unban")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User reinstated"));
        signIn(user.name()).andExpect(status().isOk());
        as(user, get("/posts/feed")).andExpect(jsonPath("$", hasSize(0)));
        as(user, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(friend, get("/user/lookup").param("username", user.name())).andExpect(status().isOk());

        as(moderator, delete("/admin/users/" + moderator.name())).andExpect(status().isBadRequest());
        as(moderator, delete("/admin/users/" + user.name())).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted"));
        assertThat(userRepository.findByUsername(user.name())).isEmpty();
        as(moderator, delete("/admin/users/" + user.name())).andExpect(status().isNotFound());
    }

    @Test
    void legalPagesArePublic() throws Exception {
        mvc.perform(get("/legal")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").isNotEmpty())
                .andExpect(jsonPath("$.privacyPolicyUrl").value("http://localhost:8080/privacy"))
                .andExpect(jsonPath("$.termsUrl").value("http://localhost:8080/terms"));
        mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Privacy Policy")))
                .andExpect(content().string(containsString("Delete account")));
        mvc.perform(get("/terms")).andExpect(status().isOk())
                .andExpect(content().string(containsString("zero tolerance")));
    }

    // ---------------------------------------------------------------- helpers
}
