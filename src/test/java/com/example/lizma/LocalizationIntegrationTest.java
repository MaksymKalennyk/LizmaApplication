package com.example.lizma;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Messages for the app come in its language (Accept-Language): English by default, Ukrainian on request. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalizationIntegrationTest extends ApiTestSupport {

    @Test
    void errorsComeInTheAppsLanguage() throws Exception {
        User user = newUser();
        String wrongPassword = objectMapper.writeValueAsString(Map.of("username", user.name(), "password", "wrong-password"));

        assertThat(message(mvc.perform(post("/auth/sign-in").contentType(APPLICATION_JSON).content(wrongPassword)
                .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")).andExpect(status().isUnauthorized())))
                .isEqualTo("Неправильний пароль");
        assertThat(message(mvc.perform(post("/auth/sign-in").contentType(APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())))
                .isEqualTo("Incorrect password");
    }

    @Test
    void validationTemplatesAndSecurityMessagesAreTranslatedToo() throws Exception {
        User user = newUser();
        // Bean validation
        assertThat(message(as(user, post("/user/feedback").contentType(APPLICATION_JSON).header("Accept-Language", "uk")
                .content("{\"category\":\"IDEA\",\"message\":\" \"}")).andExpect(status().isBadRequest())))
                .isEqualTo("Напишіть повідомлення");
        // A message with a varying part
        assertThat(message(as(user, post("/posts").contentType(APPLICATION_JSON).header("Accept-Language", "uk")
                .content("{\"musicId\":\"x\",\"musicProvider\":\"FOO\",\"trackName\":\"t\",\"artistName\":\"a\"}"))
                .andExpect(status().isBadRequest())))
                .isEqualTo("Неприпустиме значення поля «musicProvider»");
        // Spring Security answers before any controller
        assertThat(message(mvc.perform(get("/user/friends").header("Accept-Language", "uk")
                .header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized())))
                .isEqualTo("Сеанс завершився, увійдіть знову");
        // Unknown languages fall back to English
        assertThat(message(mvc.perform(get("/user/friends").header("Accept-Language", "de"))
                .andExpect(status().isUnauthorized())))
                .isEqualTo("Authentication required");
    }

    @Test
    void successMessagesAreTranslated() throws Exception {
        User user = newUser();
        long postId = createPost(user, "bye");
        assertThat(message(as(user, delete("/posts/" + postId).header("Accept-Language", "uk")).andExpect(status().isOk())))
                .isEqualTo("Допис видалено");
    }

    @Test
    void legalPagesComeInTheAppsLanguage() throws Exception {
        assertThat(json(mvc.perform(get("/legal").header("Accept-Language", "uk"))).get("privacyPolicyUrl").asText())
                .endsWith("/privacy?lang=uk");
        assertThat(json(mvc.perform(get("/legal"))).get("termsUrl").asText()).endsWith("/terms");

        String ukrainian = mvc.perform(get("/privacy").param("lang", "uk")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(ukrainian).contains("<h1>Політика конфіденційності</h1>").contains("lizma.support@gmail.com");
        String english = mvc.perform(get("/terms")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(english).contains("<h1>Terms of Use</h1>");
        assertThat(mvc.perform(get("/terms").header("Accept-Language", "uk")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("<h1>Умови використання</h1>");
    }

    private String message(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json(result).get("message").asText();
    }
}
