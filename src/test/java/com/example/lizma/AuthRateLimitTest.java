package com.example.lizma;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Password guessing and mass sign-ups are slowed down (429). */
@SpringBootTest(properties = {
        "app.auth.failures-per-username=5", "app.auth.failures-per-address=8", "app.auth.sign-ups-per-address=3",
        // Its own database: a context with other properties would otherwise recreate the shared one.
        "spring.datasource.url=jdbc:h2:mem:ratelimit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitTest extends ApiTestSupport {

    @Test
    void wrongPasswordsLockTheUsernameForAWhile() throws Exception {
        String name = nextName();
        signUpFrom("10.0.0.1", name).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            signInFrom("10.0.0.1", name, "wrong-password").andExpect(status().isUnauthorized());
        }
        // Even the right password waits now, from anywhere.
        signInFrom("10.0.0.2", name, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(startsWith("Too many attempts. Try again in")));

        // Other people are not affected.
        String other = nextName();
        signUpFrom("10.0.0.3", other).andExpect(status().isOk());
        signInFrom("10.0.0.3", other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void oneAddressCantGuessAcrossManyUsernames() throws Exception {
        for (int i = 0; i < 8; i++) {
            signInFrom("10.0.1.1", "nobody_" + i, PASSWORD).andExpect(status().isNotFound());
        }
        signInFrom("10.0.1.1", "nobody_9", PASSWORD).andExpect(status().isTooManyRequests());
        signInFrom("10.0.1.2", "nobody_9", PASSWORD).andExpect(status().isNotFound());
    }

    @Test
    void aSuccessfulSignInClearsTheUsernamesFailures() throws Exception {
        String name = nextName();
        signUpFrom("10.0.2.1", name).andExpect(status().isOk());
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                signInFrom("10.0.2." + (10 + round), name, "wrong-password").andExpect(status().isUnauthorized());
            }
            signInFrom("10.0.2." + (10 + round), name, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void signUpsPerAddressAreLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            signUpFrom("10.0.3.1", nextName()).andExpect(status().isOk());
        }
        signUpFrom("10.0.3.1", nextName()).andExpect(status().isTooManyRequests());
        signUpFrom("10.0.3.2", nextName()).andExpect(status().isOk());
    }

    private ResultActions signUpFrom(String address, String name) throws Exception {
        return mvc.perform(post("/auth/sign-up").contentType(APPLICATION_JSON)
                .with(request -> { request.setRemoteAddr(address); return request; })
                .content(objectMapper.writeValueAsString(Map.of("username", name, "password", PASSWORD, "acceptTerms", true))));
    }

    private ResultActions signInFrom(String address, String name, String password) throws Exception {
        return mvc.perform(post("/auth/sign-in").contentType(APPLICATION_JSON)
                .with(request -> { request.setRemoteAddr(address); return request; })
                .content(objectMapper.writeValueAsString(Map.of("username", name, "password", password))));
    }
}
