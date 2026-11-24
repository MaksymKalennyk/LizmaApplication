package com.example.lizma;

import com.example.lizma.model.Users;
import com.example.lizma.model.enums.Role;
import com.example.lizma.repo.FeedbackRepository;
import com.example.lizma.repo.ReportRepository;
import com.example.lizma.service.ModerationMailer;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Feedback from the app, the moderators' screens (/admin/**) and the moderation emails. */
@SpringBootTest(properties = {"spring.mail.host=smtp.test", "spring.mail.username=lizma.support@example.com",
        "app.notify.to=moderators@example.com",
        // Its own database: a context with other properties would otherwise recreate the shared one.
        "spring.datasource.url=jdbc:h2:mem:moderation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationIntegrationTest extends ApiTestSupport {

    @MockBean
    private JavaMailSender mailSender;
    @Autowired
    private ModerationMailer mailer;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;

    @BeforeEach
    void startFromAnEmptyOutbox() {
        // Other tests of this class leave reports and feedback behind; they are not what we look at.
        Instant now = Instant.now();
        reportRepository.saveAll(reportRepository.findAll().stream().peek(report -> report.setNotifiedAt(now)).toList());
        feedbackRepository.saveAll(feedbackRepository.findAll().stream().peek(feedback -> feedback.setNotifiedAt(now)).toList());
        clearInvocations(mailSender);
    }

    // ---------------------------------------------------------------- feedback

    @Test
    void feedbackIsValidatedAndLimitedPerHour() throws Exception {
        User user = newUser();
        sendFeedback(user, Map.of("message", "no category")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose what your message is about"));
        sendFeedback(user, Map.of("category", "IDEA", "message", "  ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Write a message"));
        sendFeedback(user, Map.of("category", "IDEA", "message", "x".repeat(2001))).andExpect(status().isBadRequest());
        sendFeedback(user, Map.of("category", "IDEA", "message", "hi", "contactEmail", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This doesn't look like an email address"));

        for (int i = 0; i < 5; i++) {
            sendFeedback(user, Map.of("category", "IDEA", "message", "idea " + i)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Thanks! We read every message."));
        }
        sendFeedback(user, Map.of("category", "IDEA", "message", "one too many")).andExpect(status().isTooManyRequests());
    }

    @Test
    void moderatorsReadFeedbackAndMarkItDone() throws Exception {
        User user = newUser();
        User moderator = newModerator();
        sendFeedback(user, Map.of("category", "PROBLEM", "message", "The player doesn't start",
                "contactEmail", "me@example.com", "appVersion", "1.0 (1)", "osVersion", "iOS 26.3")).andExpect(status().isOk());

        as(user, get("/admin/feedback")).andExpect(status().isForbidden());
        JsonNode inbox = json(as(moderator, get("/admin/feedback")).andExpect(status().isOk()));
        JsonNode mine = find(inbox, "authorUsername", user.name());
        assertThat(mine.get("message").asText()).isEqualTo("The player doesn't start");
        assertThat(mine.get("contactEmail").asText()).isEqualTo("me@example.com");
        assertThat(mine.get("appVersion").asText()).isEqualTo("1.0 (1)");
        long summaryBefore = json(as(moderator, get("/admin/summary"))).get("newFeedback").asLong();

        as(moderator, post("/admin/feedback/" + mine.get("id").asLong() + "/done")).andExpect(status().isOk());
        assertThat(json(as(moderator, get("/admin/summary"))).get("newFeedback").asLong()).isEqualTo(summaryBefore - 1);
        JsonNode done = find(json(as(moderator, get("/admin/feedback").param("status", "DONE"))), "authorUsername", user.name());
        assertThat(done.get("handledByUsername").asText()).isEqualTo(moderator.name());

        as(moderator, post("/admin/feedback/" + mine.get("id").asLong() + "/reopen")).andExpect(status().isOk());
        assertThat(find(json(as(moderator, get("/admin/feedback"))), "authorUsername", user.name())
                .get("handledByUsername").isNull()).isTrue();
    }

    // ---------------------------------------------------------------- admin screens

    @Test
    void meTellsTheAppWhoIsAModerator() throws Exception {
        User user = newUser();
        User moderator = newModerator();
        as(user, get("/user/me")).andExpect(jsonPath("$.username").value(user.name()))
                .andExpect(jsonPath("$.admin").value(false));
        as(moderator, get("/user/me")).andExpect(jsonPath("$.admin").value(true));
        as(user, get("/admin/summary")).andExpect(status().isForbidden());
    }

    @Test
    void handledReportsShowWhoDecidedAndTheLogKeepsEveryAction() throws Exception {
        User author = newUser();
        User reporter = newUser();
        User moderator = newModerator();
        makeFriends(author, reporter);
        long postId = createPost(author, "spam spam");
        report(reporter, "POST", postId, "SPAM").andExpect(status().isOk());
        long reportId = findReport(json(as(moderator, get("/admin/reports"))), "POST", postId).get("id").asLong();

        resolve(moderator, reportId, "REMOVE_CONTENT").andExpect(status().isOk());
        JsonNode handled = findReport(json(as(moderator, get("/admin/reports")
                .param("status", "RESOLVED").param("status", "DISMISSED"))), "POST", postId);
        assertThat(handled.get("resolution").asText()).isEqualTo("REMOVE_CONTENT");
        assertThat(handled.get("resolvedByUsername").asText()).isEqualTo(moderator.name());

        as(moderator, post("/admin/users/" + author.name() + "/ban")).andExpect(status().isOk());
        as(moderator, post("/admin/users/" + author.name() + "/unban")).andExpect(status().isOk());

        JsonNode log = json(as(moderator, get("/admin/log")));
        List<String> actions = new java.util.ArrayList<>();
        for (JsonNode entry : log) {
            if (author.name().equals(entry.path("targetUsername").asText())) {
                actions.add(entry.get("action").asText() + " by " + entry.get("moderatorUsername").asText());
            }
        }
        assertThat(actions).containsExactly(
                "UNBAN_USER by " + moderator.name(),
                "BAN_USER by " + moderator.name(),
                "REMOVE_CONTENT by " + moderator.name());
    }

    @Test
    void moderatorsFindSuspendedUsersButNotAnotherModeratorToBan() throws Exception {
        User user = newUser();
        User moderator = newModerator();
        User otherModerator = newModerator();

        as(moderator, post("/admin/users/" + user.name() + "/ban")).andExpect(status().isOk());
        JsonNode suspended = json(as(moderator, get("/admin/users")));
        assertThat(find(suspended, "username", user.name()).get("banned").asBoolean()).isTrue();
        JsonNode found = json(as(moderator, get("/admin/users").param("query", otherModerator.name())));
        assertThat(find(found, "username", otherModerator.name()).get("admin").asBoolean()).isTrue();

        as(moderator, post("/admin/users/" + otherModerator.name() + "/ban")).andExpect(status().isBadRequest());
        as(moderator, delete("/admin/users/" + otherModerator.name())).andExpect(status().isBadRequest());
        // A moderator can't delete their own account in the app either.
        as(moderator, post("/user/me/delete").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingAModeratorsAccountKeepsTheirDecisionsWithoutTheirName() throws Exception {
        User author = newUser();
        User reporter = newUser();
        User moderator = newModerator();
        makeFriends(author, reporter);
        long postId = createPost(author, "meh");
        report(reporter, "POST", postId, "OTHER").andExpect(status().isOk());
        long reportId = findReport(json(as(moderator, get("/admin/reports"))), "POST", postId).get("id").asLong();
        resolve(moderator, reportId, "DISMISS").andExpect(status().isOk());
        sendFeedback(moderator, Map.of("category", "OTHER", "message", "note to self")).andExpect(status().isOk());

        // No longer in the admin list: now the account can go.
        Users entity = userRepository.findById(moderator.id()).orElseThrow();
        entity.setRole(Role.ROLE_USER);
        userRepository.save(entity);
        as(moderator, post("/user/me/delete").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD)))).andExpect(status().isOk());

        User another = newModerator();
        JsonNode handled = findReport(json(as(another, get("/admin/reports").param("status", "DISMISSED"))), "POST", postId);
        assertThat(handled.get("resolvedByUsername").isNull()).isTrue();
        assertThat(handled.get("resolution").asText()).isEqualTo("DISMISS");
        for (JsonNode feedback : json(as(another, get("/admin/feedback")))) {
            assertThat(feedback.get("authorUsername").asText()).isNotEqualTo(moderator.name());
        }
    }

    @Test
    void reportingIsLimitedPerHour() throws Exception {
        User reporter = newUser();
        for (int i = 0; i < 30; i++) {
            report(reporter, "USER", newUser().id(), "SPAM").andExpect(status().isOk());
        }
        report(reporter, "USER", newUser().id(), "SPAM").andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- emails

    @Test
    void newReportsAndFeedbackGoOutOnceInADigest() throws Exception {
        User author = newUser();
        User reporter = newUser();
        makeFriends(author, reporter);
        long postId = createPost(author, "Buy followers here");
        report(reporter, "POST", postId, "SPAM").andExpect(status().isOk());
        sendFeedback(reporter, Map.of("category", "IDEA", "message", "Dark mode for the composer")).andExpect(status().isOk());
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(mailer.sendPendingNow()).isEqualTo(1);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage digest = sent.getValue();
        assertThat(digest.getTo()).containsExactly("moderators@example.com");
        assertThat(digest.getFrom()).isEqualTo("lizma.support@example.com");
        assertThat(digest.getSubject()).isEqualTo("[Lizma] 1 new report, 1 new feedback");
        assertThat(digest.getText())
                .contains("Post by @" + author.name() + "  ·  SPAM")
                .contains("Buy followers here")
                .contains("Reported by @" + reporter.name())
                .contains("Idea from @" + reporter.name())
                .contains("Dark mode for the composer")
                .contains("Profile → Moderation");

        // Already mailed: nothing goes out again.
        clearInvocations(mailSender);
        assertThat(mailer.sendPendingNow()).isZero();
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
    }

    @Test
    void feedbackWithAContactEmailGetsItsOwnMailToReplyTo() throws Exception {
        User user = newUser();
        sendFeedback(user, Map.of("category", "PROBLEM", "message", "Comments don't load",
                "contactEmail", "user@example.com")).andExpect(status().isOk());
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(mailer.sendPendingNow()).isEqualTo(1);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("[Lizma] Problem from @" + user.name());
        assertThat(sent.getValue().getReplyTo()).isEqualTo("user@example.com");
        assertThat(sent.getValue().getText()).contains("Comments don't load").contains("Reply to this email");
    }

    @Test
    void whatModeratorsHandledBeforeTheMailWentOutIsNotMailed() throws Exception {
        User author = newUser();
        User reporter = newUser();
        User moderator = newModerator();
        makeFriends(author, reporter);
        long postId = createPost(author, "borderline");
        report(reporter, "POST", postId, "OTHER").andExpect(status().isOk());
        long reportId = findReport(json(as(moderator, get("/admin/reports"))), "POST", postId).get("id").asLong();
        resolve(moderator, reportId, "DISMISS").andExpect(status().isOk());
        sendFeedback(reporter, Map.of("category", "IDEA", "message", "Already answered")).andExpect(status().isOk());
        long feedbackId = find(json(as(moderator, get("/admin/feedback"))), "authorUsername", reporter.name()).get("id").asLong();
        as(moderator, post("/admin/feedback/" + feedbackId + "/done")).andExpect(status().isOk());

        assertThat(mailer.sendPendingNow()).isZero();
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
        // They count as mailed: reopening the feedback later doesn't bring a mail about it either.
        as(moderator, post("/admin/feedback/" + feedbackId + "/reopen")).andExpect(status().isOk());
        assertThat(mailer.sendPendingNow()).isZero();
    }

    @Test
    void aFailedSendIsRetriedOnTheNextRun() throws Exception {
        User user = newUser();
        sendFeedback(user, Map.of("category", "OTHER", "message", "Hello")).andExpect(status().isOk());

        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThat(mailer.sendPendingNow()).isZero();

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        clearInvocations(mailSender);
        assertThat(mailer.sendPendingNow()).isEqualTo(1);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ---------------------------------------------------------------- helpers

    private User newModerator() throws Exception {
        User user = newUser();
        makeModerator(user);
        return user;
    }

    private org.springframework.test.web.servlet.ResultActions sendFeedback(User user, Map<String, Object> body) throws Exception {
        return as(user, post("/user/feedback").contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HashMap<>(body))));
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }
}
