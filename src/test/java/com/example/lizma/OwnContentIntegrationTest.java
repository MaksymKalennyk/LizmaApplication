package com.example.lizma;

import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.UserMusicAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What users can undo themselves: friendships, their posts and comments, a connected Spotify account. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnContentIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserMusicAccountRepository musicAccounts;

    @Test
    void removingAFriendHidesTheirPostsAndAllowsANewRequest() throws Exception {
        User alice = newUser();
        User bob = newUser();
        makeFriends(alice, bob);
        long bobPost = createPost(bob, "for alice");
        as(alice, get("/posts/feed")).andExpect(jsonPath("$", hasSize(1)));

        as(alice, delete("/user/friends/" + bob.name())).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Removed from friends"));
        as(alice, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(bob, get("/user/friends")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, get("/posts/feed")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, get("/posts/" + bobPost + "/comments")).andExpect(status().isNotFound());

        // Nothing left over blocks a new request, and removing again is harmless.
        as(alice, delete("/user/friends/" + bob.name())).andExpect(status().isOk());
        as(bob, post("/user/friend-requests/send/" + alice.id())).andExpect(status().isOk());
        as(alice, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(1)));

        as(alice, delete("/user/friends/" + alice.name())).andExpect(status().isBadRequest());
        as(alice, delete("/user/friends/nobody_" + alice.name())).andExpect(status().isNotFound());
    }

    @Test
    void theSenderCancelsAPendingRequestAndCanSendItAgain() throws Exception {
        User alice = newUser();
        User bob = newUser();
        User carol = newUser();
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());
        long requestId = json(as(alice, get("/user/friend-requests/sent"))).get(0).get("id").asLong();

        // Only the sender, and only while it waits for an answer.
        as(bob, delete("/user/friend-requests/" + requestId)).andExpect(status().isForbidden());
        as(carol, delete("/user/friend-requests/" + requestId)).andExpect(status().isForbidden());
        as(alice, delete("/user/friend-requests/" + requestId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Friend request cancelled"));
        as(alice, get("/user/friend-requests/sent")).andExpect(jsonPath("$", hasSize(0)));
        as(bob, get("/user/friend-requests/received")).andExpect(jsonPath("$", hasSize(0)));
        as(alice, delete("/user/friend-requests/" + requestId)).andExpect(status().isNotFound());

        // Sending again works, and an answered request can't be cancelled.
        as(alice, post("/user/friend-requests/send/" + bob.id())).andExpect(status().isOk());
        long again = json(as(alice, get("/user/friend-requests/sent"))).get(0).get("id").asLong();
        as(bob, put("/user/friend-requests/respond/" + again).param("status", "DECLINED")).andExpect(status().isOk());
        as(alice, delete("/user/friend-requests/" + again)).andExpect(status().isConflict());
    }

    @Test
    void onlyTheAuthorDeletesAPostAndItTakesItsCommentsAndReactions() throws Exception {
        User author = newUser();
        User friend = newUser();
        makeFriends(author, friend);
        long postId = createPost(author, "short-lived");
        long keptPost = createPost(author, "stays");
        long commentId = commentId(as(friend, comment(postId, "nice", null)));
        as(author, comment(postId, "thanks", commentId)).andExpect(status().isOk());
        as(friend, post("/posts/" + postId + "/react").param("type", "🔥")).andExpect(status().isOk());

        as(friend, delete("/posts/" + postId)).andExpect(status().isForbidden());
        as(author, delete("/posts/" + postId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post deleted"));

        as(friend, get("/posts/feed")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(keptPost));
        as(friend, get("/posts/" + postId + "/comments")).andExpect(status().isNotFound());
        as(author, delete("/posts/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void commentsAreDeletedByTheirAuthorOrThePostAuthor() throws Exception {
        User author = newUser();
        User friend = newUser();
        User other = newUser();
        makeFriends(author, friend);
        makeFriends(author, other);
        long postId = createPost(author, "talk");

        long friendComment = commentId(as(friend, comment(postId, "first", null)));
        long otherReply = commentId(as(other, comment(postId, "reply", friendComment)));
        long otherComment = commentId(as(other, comment(postId, "second", null)));
        long authorComment = commentId(as(author, comment(postId, "third", null)));

        // Someone else's comment on someone else's post: not allowed.
        as(friend, delete("/posts/" + postId + "/comments/" + otherComment)).andExpect(status().isForbidden());
        // Your own reply.
        as(other, delete("/posts/" + postId + "/comments/" + otherReply)).andExpect(status().isOk());
        // The post's author removes a friend's comment.
        as(author, delete("/posts/" + postId + "/comments/" + otherComment)).andExpect(status().isOk());
        // Your own comment; a comment of another post's id is not found.
        as(friend, delete("/posts/" + createPost(author, "elsewhere") + "/comments/" + friendComment))
                .andExpect(status().isNotFound());
        as(friend, delete("/posts/" + postId + "/comments/" + friendComment)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Comment deleted"));

        JsonNode comments = json(as(author, get("/posts/" + postId + "/comments")));
        assertThat(comments.findValuesAsText("id")).containsExactly(String.valueOf(authorComment));
        as(author, get("/posts/feed")).andExpect(jsonPath("$[1].commentsCount").value(1));
    }

    @Test
    void deletingATopLevelCommentTakesItsReplies() throws Exception {
        User author = newUser();
        User friend = newUser();
        makeFriends(author, friend);
        long postId = createPost(author, "thread");
        long top = commentId(as(friend, comment(postId, "top", null)));
        as(author, comment(postId, "reply 1", top)).andExpect(status().isOk());
        as(friend, comment(postId, "reply 2", top)).andExpect(status().isOk());

        as(friend, delete("/posts/" + postId + "/comments/" + top)).andExpect(status().isOk());
        as(author, get("/posts/" + postId + "/comments")).andExpect(jsonPath("$", hasSize(0)));
        as(author, get("/posts/feed")).andExpect(jsonPath("$[0].commentsCount").value(0));
    }

    @Test
    void disconnectingSpotifyForgetsTheTokens() throws Exception {
        User user = newUser();
        musicAccounts.save(UserMusicAccount.builder()
                .user(userRepository.findById(user.id()).orElseThrow())
                .provider(MusicProvider.SPOTIFY)
                .spotifyAccessToken("access")
                .spotifyRefreshToken("refresh")
                .spotifyAccessTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build());
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(true));

        as(user, delete("/music/spotify")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Spotify disconnected"));
        as(user, get("/music/status")).andExpect(jsonPath("$.spotifyConnected").value(false));
        as(user, get("/music/search").param("query", "song")).andExpect(status().isConflict());
        // Again: nothing to forget, still fine.
        as(user, delete("/music/spotify")).andExpect(status().isOk());
    }
}
