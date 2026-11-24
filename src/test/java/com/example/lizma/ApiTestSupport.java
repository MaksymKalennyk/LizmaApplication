package com.example.lizma;

import com.example.lizma.model.Users;
import com.example.lizma.model.enums.Role;
import com.example.lizma.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Users, posts, comments and reports through the API, for the MockMvc integration tests. */
abstract class ApiTestSupport {

    protected static final String PASSWORD = "password123";
    protected static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;

    protected record User(long id, String name, String token) {
    }

    protected String nextName() {
        return "test_user_" + SEQ.incrementAndGet();
    }

    protected User newUser() throws Exception {
        String name = nextName();
        String token = json(signUp(Map.of("username", name, "password", PASSWORD, "acceptTerms", true))
                .andExpect(status().isOk())).get("token").asText();
        long id = userRepository.findByUsername(name).orElseThrow().getId();
        return new User(id, name, token);
    }

    protected ResultActions signUp(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/auth/sign-up").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions signIn(String username) throws Exception {
        return mvc.perform(post("/auth/sign-in").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))));
    }

    protected ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token()));
    }

    protected void makeFriends(User inviter, User invitee) throws Exception {
        String token = json(as(inviter, post("/user/friend-invites"))).get("token").asText();
        as(invitee, post("/user/friend-invites/" + token + "/accept")).andExpect(status().isOk());
    }

    protected void makeModerator(User user) {
        Users entity = userRepository.findByUsername(user.name()).orElseThrow();
        entity.setRole(Role.ROLE_ADMIN);
        userRepository.save(entity);
    }

    protected long createPost(User author, String caption) throws Exception {
        return json(as(author, post("/posts").contentType(APPLICATION_JSON).content(postJson(caption)))
                .andExpect(status().isOk())).get("id").asLong();
    }

    protected MockHttpServletRequestBuilder comment(long postId, String text, Long parentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("content", text);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        return post("/posts/" + postId + "/comments").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected long commentId(ResultActions result) throws Exception {
        return json(result.andExpect(status().isOk())).get("id").asLong();
    }

    protected ResultActions report(User reporter, String type, long targetId, String reason) throws Exception {
        return as(reporter, post("/reports").contentType(APPLICATION_JSON).content(
                "{\"targetType\":\"" + type + "\",\"targetId\":" + targetId + ",\"reason\":\"" + reason + "\"}"));
    }

    protected ResultActions resolve(User moderator, long reportId, String action) throws Exception {
        return as(moderator, post("/admin/reports/" + reportId + "/resolve").contentType(APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"));
    }

    protected ResultActions deleteAccount(User user, String password) throws Exception {
        return as(user, post("/user/me/delete").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", password))));
    }

    protected static List<JsonNode> reportsAbout(JsonNode reports, String username) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode report : reports) {
            if (username.equals(report.path("targetUsername").asText())) {
                found.add(report);
            }
        }
        return found;
    }

    protected static List<JsonNode> reportsOn(JsonNode reports, String type, long targetId) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode report : reports) {
            if (report.get("targetType").asText().equals(type) && report.get("targetId").asLong() == targetId) {
                found.add(report);
            }
        }
        return found;
    }

    protected static JsonNode findReport(JsonNode reports, String type, long targetId) {
        return reportsOn(reports, type, targetId).stream().findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " report for " + targetId + " in " + reports));
    }

    protected String postJson(String caption) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "musicId", "track-1", "musicProvider", "SPOTIFY",
                "trackName", "Song", "artistName", "Artist", "caption", caption));
    }

    protected JsonNode json(ResultActions result) throws Exception {
        // JSON is UTF-8; MockMvc would otherwise decode it as ISO-8859-1 and mangle emoji.
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
