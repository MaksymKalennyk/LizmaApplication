package com.example.lizma;

import com.example.lizma.model.Users;
import com.example.lizma.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Short-lived access tokens, rotating refresh tokens, sign-out and what ends a session. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionIntegrationTest extends ApiTestSupport {

    @Test
    void signInGivesAShortAccessTokenAndARefreshToken() throws Exception {
        User user = newUser();
        JsonNode tokens = json(signIn(user.name()).andExpect(status().isOk()));
        assertThat(tokens.get("token").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).hasSizeGreaterThan(40);
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(15 * 60);
    }

    @Test
    void refreshingRotatesAndAReusedTokenEndsTheSession() throws Exception {
        User user = newUser();
        String first = json(signIn(user.name())).get("refreshToken").asText();

        JsonNode renewed = json(refresh(first).andExpect(status().isOk()));
        String second = renewed.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/user/me").header("Authorization", "Bearer " + renewed.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.name()));

        // The first token was copied and used again: the whole session ends, the newer token too.
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expired, please sign in again"));
        refresh(second).andExpect(status().isUnauthorized());

        // Another sign-in is a separate session and keeps working.
        refresh(json(signIn(user.name())).get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        User user = newUser();
        String refreshToken = json(signIn(user.name())).get("refreshToken").asText();

        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content(body(refreshToken)))
                .andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isUnauthorized());
        // Unknown or already ended sessions: nothing to do, still fine.
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content(body("no-such-token")))
                .andExpect(status().isOk());
        refresh("no-such-token").andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void aSuspendedUserCantRefresh() throws Exception {
        User user = newUser();
        User moderator = newUser();
        Users entity = userRepository.findById(moderator.id()).orElseThrow();
        entity.setRole(Role.ROLE_ADMIN);
        userRepository.save(entity);
        String refreshToken = json(signIn(user.name())).get("refreshToken").asText();

        as(moderator, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isUnauthorized());
        as(moderator, post("/admin/users/" + user.name() + "/unban")).andExpect(status().isOk());
        // Lifting the suspension doesn't bring old sessions back.
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeletedAccountsTokenDoesntOpenANewAccountWithTheSameName() throws Exception {
        User old = newUser();
        as(old, post("/user/me/delete").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD)))).andExpect(status().isOk());
        signUp(Map.of("username", old.name(), "password", PASSWORD, "acceptTerms", true)).andExpect(status().isOk());

        as(old, get("/user/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expired, please sign in again"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content(body(refreshToken)));
    }

    private String body(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
    }
}
