package com.example.lizma;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Any emoji works as a reaction and inside comments, however it's put together; text isn't a reaction. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmojiIntegrationTest extends ApiTestSupport {

    /** Simple, with a skin tone, joined into one, flags, a keycap, text style made emoji, and newer than Java 21. */
    static final List<String> TRICKY = List.of(
            "❤️", "🔥", "👍🏽", "👨‍👩‍👧‍👦", "🏳️‍🌈", "🧑🏽‍💻", "😶‍🌫️", "❤️‍🔥", "👩🏻‍❤️‍💋‍👨🏼",
            "🇺🇦", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "1️⃣", "#️⃣", "☺️", "🫨", "🫩");

    @Test
    void everyKindOfEmojiIsAReaction() throws Exception {
        User author = newUser();
        long postId = createPost(author, "react to me");
        for (String emoji : TRICKY) {
            User fan = newUser();
            makeFriends(author, fan);
            as(fan, post("/posts/" + postId + "/react").param("type", emoji)).andExpect(status().isOk());
        }

        JsonNode counts = json(as(author, get("/posts/" + postId))).get("reactionsCount");
        for (String emoji : TRICKY) {
            assertThat(counts.has(emoji)).as("reaction %s", emoji).isTrue();
        }
    }

    @Test
    void textOrTwoEmojiIsNotAReaction() throws Exception {
        User author = newUser();
        User fan = newUser();
        makeFriends(author, fan);
        long postId = createPost(author, "react to me");
        for (String notOne : List.of("lol", "1", "🔥🔥", ":)", "a🔥")) {
            as(fan, post("/posts/" + postId + "/react").param("type", notOne))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A reaction is one emoji"));
        }
    }

    @Test
    void commentsKeepEveryEmojiAsWritten() throws Exception {
        User author = newUser();
        User fan = newUser();
        makeFriends(author, fan);
        long postId = createPost(author, "say something");
        String text = "Love it " + String.join(" ", TRICKY) + " 🎶";

        as(fan, comment(postId, text, null)).andExpect(status().isOk());

        JsonNode comments = json(as(author, get("/posts/" + postId + "/comments")));
        assertThat(comments.get(0).get("content").asText()).isEqualTo(text);
    }
}
