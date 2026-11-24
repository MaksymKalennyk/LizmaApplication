package com.example.lizma;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The feed costs a few queries however many posts it has, and the cursor pages without gaps or repeats. */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // Its own database: a context with other properties would otherwise recreate the shared one.
        "spring.datasource.url=jdbc:h2:mem:feedqueries;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
// The notifications about the reactions and comments made below must not run during the counted request.
@Import(SyncBackgroundWork.class)
class FeedQueriesTest extends ApiTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void aFullFeedPageTakesAFixedNumberOfQueries() throws Exception {
        User viewer = newUser();
        // Friends with every author too, so they can react and comment.
        User fan = newUser();
        List<User> friends = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User friend = newUser();
            makeFriends(viewer, friend);
            makeFriends(fan, friend);
            friends.add(friend);
        }
        for (int i = 0; i < 24; i++) {
            User author = friends.get(i % friends.size());
            long postId = createPost(author, "post " + i);
            as(fan, post("/posts/" + postId + "/react").param("type", i % 2 == 0 ? "🔥" : "❤️")).andExpect(status().isOk());
            as(viewer, post("/posts/" + postId + "/react").param("type", "🎵")).andExpect(status().isOk());
            as(fan, comment(postId, "comment " + i, null)).andExpect(status().isOk());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        JsonNode feed = json(as(viewer, get("/posts/feed").param("size", "20")).andExpect(status().isOk()));
        long statements = statistics.getPrepareStatementCount();

        assertThat(feed).hasSize(20);
        // viewer, friends, posts with authors, reaction counts, the viewer's reactions, comment counts
        assertThat(statements).isLessThanOrEqualTo(7);
        JsonNode newest = feed.get(0);
        assertThat(newest.get("caption").asText()).isEqualTo("post 23");
        assertThat(newest.get("commentsCount").asLong()).isEqualTo(1);
        assertThat(newest.get("currentUserReaction").asText()).isEqualTo("🎵");
        assertThat(newest.get("reactionsCount").toString()).as(newest.toString())
                .isEqualTo("{\"🎵\":1,\"❤️\":1}");
    }

    @Test
    void theCursorPagesWithoutRepeatsWhenNewPostsArrive() throws Exception {
        User viewer = newUser();
        User friend = newUser();
        makeFriends(viewer, friend);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            created.add(createPost(friend, "p" + i));
        }

        JsonNode first = json(as(viewer, get("/posts/feed").param("size", "3")));
        assertThat(ids(first)).containsExactly(created.get(6), created.get(5), created.get(4));

        // A new post on top doesn't push anything into the next page twice.
        createPost(friend, "newest");
        JsonNode second = json(as(viewer, get("/posts/feed").param("size", "3")
                .param("before", String.valueOf(created.get(4)))));
        assertThat(ids(second)).containsExactly(created.get(3), created.get(2), created.get(1));
        JsonNode last = json(as(viewer, get("/posts/feed").param("size", "3")
                .param("before", String.valueOf(created.get(1)))));
        assertThat(ids(last)).containsExactly(created.get(0));

        // The page parameter still works for apps that use it.
        assertThat(ids(json(as(viewer, get("/posts/feed").param("page", "1").param("size", "3")))))
                .containsExactly(created.get(4), created.get(3), created.get(2));
    }

    private static List<Long> ids(JsonNode feed) {
        List<Long> ids = new ArrayList<>();
        feed.forEach(post -> ids.add(post.get("id").asLong()));
        return ids;
    }
}
