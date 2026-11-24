package com.example.lizma;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profiles: display name, photo, who sees what, and moderators taking a profile down. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest extends ApiTestSupport {

    @Test
    void theNameAndPhotoShowWhereverTheUserAppears() throws Exception {
        User anna = newUser();
        User bob = newUser();
        makeFriends(anna, bob);

        as(anna, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"  Anna   K.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anna K."))
                .andExpect(jsonPath("$.relationship").value("SELF"));
        String avatarUrl = json(uploadAvatar(anna, png(800, 600)).andExpect(status().isOk())).get("avatarUrl").asText();
        assertThat(avatarUrl).matches("/avatars/[0-9a-f]{32}\\.jpg");

        long postId = createPost(anna, "hello");
        as(bob, comment(postId, "nice", null)).andExpect(status().isOk());
        JsonNode feedPost = json(as(bob, get("/posts/feed"))).get(0);
        assertThat(feedPost.get("authorDisplayName").asText()).isEqualTo("Anna K.");
        assertThat(feedPost.get("authorAvatarUrl").asText()).isEqualTo(avatarUrl);
        as(bob, get("/user/friends")).andExpect(jsonPath("$[0].friendDisplayName").value("Anna K."))
                .andExpect(jsonPath("$[0].friendAvatarUrl").value(avatarUrl));
        as(bob, get("/user/search").param("query", anna.name())).andExpect(jsonPath("$[0].displayName").value("Anna K."))
                .andExpect(jsonPath("$[0].avatarUrl").value(avatarUrl));
        as(anna, get("/posts/" + postId + "/comments")).andExpect(jsonPath("$[0].authorUsername").value(bob.name()))
                .andExpect(jsonPath("$[0].authorAvatarUrl").isEmpty());
        as(anna, get("/user/me")).andExpect(jsonPath("$.avatarUrl").value(avatarUrl));

        // The photo itself: public, a square 512×512 JPEG, cacheable for good.
        byte[] jpeg = mvc.perform(get(avatarUrl)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);

        // A new photo gets a new address; the old one is gone.
        String newUrl = json(uploadAvatar(anna, png(300, 300))).get("avatarUrl").asText();
        assertThat(newUrl).isNotEqualTo(avatarUrl);
        mvc.perform(get(avatarUrl)).andExpect(status().isNotFound());
        as(anna, delete("/user/me/avatar")).andExpect(status().isOk());
        mvc.perform(get(newUrl)).andExpect(status().isNotFound());
        as(anna, get("/user/me")).andExpect(jsonPath("$.avatarUrl").isEmpty());

        // Blank removes the name.
        as(anna, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"   \"}"))
                .andExpect(jsonPath("$.displayName").isEmpty());
    }

    @Test
    void namesAndPhotosAreChecked() throws Exception {
        User user = newUser();
        as(user, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());
        as(user, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"fuck off\"}"))
                .andExpect(status().isBadRequest());
        uploadAvatar(user, "not an image".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This photo can't be read. Use a JPEG or PNG."));
        uploadAvatar(user, png(20, 20)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The photo is too small"));
    }

    @Test
    void postsOnAProfileAreForFriendsOnly() throws Exception {
        User anna = newUser();
        User friend = newUser();
        User stranger = newUser();
        makeFriends(anna, friend);
        long first = createPost(anna, "first");
        long second = createPost(anna, "second");

        as(friend, get("/user/profiles/" + anna.name())).andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship").value("FRIEND"))
                .andExpect(jsonPath("$.canSeePosts").value(true))
                .andExpect(jsonPath("$.postsCount").value(2));
        as(friend, get("/user/profiles/" + anna.name() + "/posts")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second));
        as(friend, get("/user/profiles/" + anna.name() + "/posts").param("before", String.valueOf(second)))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(first));
        as(friend, get("/posts/" + first)).andExpect(status().isOk()).andExpect(jsonPath("$.caption").value("first"));

        as(stranger, get("/user/profiles/" + anna.name())).andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship").value("NONE"))
                .andExpect(jsonPath("$.canSeePosts").value(false))
                .andExpect(jsonPath("$.postsCount").isEmpty());
        as(stranger, get("/user/profiles/" + anna.name() + "/posts")).andExpect(status().isForbidden());
        as(stranger, get("/posts/" + first)).andExpect(status().isNotFound());

        // A pending request shows on both sides, with its id to cancel or answer it.
        as(stranger, post("/user/friend-requests/send/" + anna.id())).andExpect(status().isOk());
        long requestId = json(as(stranger, get("/user/profiles/" + anna.name()))).get("requestId").asLong();
        as(stranger, get("/user/profiles/" + anna.name())).andExpect(jsonPath("$.relationship").value("REQUEST_SENT"));
        as(anna, get("/user/profiles/" + stranger.name())).andExpect(jsonPath("$.relationship").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        // Blocked either way: the profile doesn't exist.
        as(anna, post("/user/blocks/" + stranger.id())).andExpect(status().isOk());
        as(stranger, get("/user/profiles/" + anna.name())).andExpect(status().isNotFound());
        as(anna, get("/user/profiles/" + stranger.name())).andExpect(status().isNotFound());
    }

    @Test
    void moderatorsTakeDownAReportedProfileAndBanningClearsIt() throws Exception {
        User user = newUser();
        User reporter = newUser();
        User moderator = newUser();
        makeModerator(moderator);
        as(user, put("/user/me/profile").contentType(APPLICATION_JSON).content("{\"displayName\":\"Spam King\"}"));
        String avatarUrl = json(uploadAvatar(user, png(200, 200))).get("avatarUrl").asText();

        report(reporter, "USER", user.id(), "SPAM").andExpect(status().isOk());
        JsonNode reportView = findReport(json(as(moderator, get("/admin/reports"))), "USER", user.id());
        assertThat(reportView.get("targetPreview").asText()).isEqualTo("Spam King (@" + user.name() + ")");
        assertThat(reportView.get("targetArtworkUrl").asText()).isEqualTo(avatarUrl);

        resolve(moderator, reportView.get("id").asLong(), "REMOVE_CONTENT").andExpect(status().isOk());
        as(reporter, get("/user/profiles/" + user.name())).andExpect(jsonPath("$.displayName").isEmpty())
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
        mvc.perform(get(avatarUrl)).andExpect(status().isNotFound());
        assertThat(json(as(moderator, get("/admin/log"))).get(0).get("action").asText()).isEqualTo("REMOVE_PROFILE");

        // Suspension clears the profile as well, and deleting the account removes the photo.
        String second = json(uploadAvatar(user, png(200, 200))).get("avatarUrl").asText();
        as(moderator, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isOk());
        mvc.perform(get(second)).andExpect(status().isNotFound());

        User leaving = newUser();
        String leavingUrl = json(uploadAvatar(leaving, png(200, 200))).get("avatarUrl").asText();
        as(leaving, post("/user/me/delete").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD)))).andExpect(status().isOk());
        mvc.perform(get(leavingUrl)).andExpect(status().isNotFound());
    }

    private ResultActions uploadAvatar(User user, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        return as(user, multipart("/user/me/avatar").file(file).with(request -> {
            request.setMethod("PUT");
            return request;
        }));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
