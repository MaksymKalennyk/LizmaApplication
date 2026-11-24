package com.example.lizma;

import com.example.lizma.model.Attachment;
import com.example.lizma.repo.AttachmentRepository;
import com.example.lizma.service.AttachmentService;
import com.example.lizma.service.ModerationMailer;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Screenshots attached to feedback and reports: uploaded, attached, seen only by moderators, cleaned up. */
@SpringBootTest(properties = {"spring.mail.host=smtp.test", "app.notify.to=moderators@example.com",
        "spring.datasource.url=jdbc:h2:mem:attachments;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentsIntegrationTest extends ApiTestSupport {

    @MockBean
    private JavaMailSender mailSender;
    @Autowired
    private ModerationMailer mailer;
    @Autowired
    private AttachmentRepository attachments;
    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void aScreenshotIsKeptAsAJpegThatOnlyModeratorsCanOpen() throws Exception {
        User user = newUser();
        User moderator = newUser();
        makeModerator(moderator);

        String id = upload(user, png(1206, 2622));
        sendFeedback(user, Map.of("category", "PROBLEM", "message", "The feed is empty", "attachmentIds", List.of(id)))
                .andExpect(status().isOk());

        JsonNode feedback = mine(json(as(moderator, get("/admin/feedback"))), user);
        assertThat(feedback.get("attachments")).hasSize(1);
        String url = feedback.get("attachments").get(0).asText();
        assertThat(url).isEqualTo("/admin/attachments/" + id + ".jpg");

        byte[] jpeg = as(moderator, get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(stored.getHeight()).isEqualTo(2000); // the long side, made smaller
        assertThat(stored.getWidth()).isEqualTo(920);   // in proportion

        as(user, get(url)).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void aReportCarriesItsScreenshotsToTheModerators() throws Exception {
        User author = newUser();
        User reporter = newUser();
        User moderator = newUser();
        makeModerator(moderator);
        makeFriends(author, reporter);
        long postId = createPost(author, "spam");
        String first = upload(reporter, png(300, 600));
        String second = upload(reporter, png(600, 300));

        as(reporter, post("/reports").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                "targetType", "POST", "targetId", postId, "reason", "SPAM", "attachmentIds", List.of(first, second)))))
                .andExpect(status().isOk());

        JsonNode report = findReport(json(as(moderator, get("/admin/reports"))), "POST", postId);
        assertThat(texts(report.get("attachments")))
                .containsExactly("/admin/attachments/" + first + ".jpg", "/admin/attachments/" + second + ".jpg");

        // The moderators' email says there are screenshots, without carrying them.
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        mailer.sendPendingNow();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).anySatisfy(mail ->
                assertThat(mail.getText()).contains("2 screenshots: open them in the app, Profile → Moderation"));
    }

    @Test
    void onlyYourOwnUnsentScreenshotsAndAtMostThree() throws Exception {
        User user = newUser();
        User other = newUser();
        String othersScreenshot = upload(other, png(200, 200));

        sendFeedback(user, Map.of("category", "IDEA", "message", "Hi", "attachmentIds", List.of(othersScreenshot)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A screenshot couldn't be attached. Add it again."));

        String mine = upload(user, png(200, 200));
        sendFeedback(user, Map.of("category", "IDEA", "message", "First", "attachmentIds", List.of(mine)))
                .andExpect(status().isOk());
        sendFeedback(user, Map.of("category", "IDEA", "message", "Again", "attachmentIds", List.of(mine)))
                .andExpect(status().isBadRequest());

        List<String> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(upload(user, png(100, 100)));
        }
        sendFeedback(user, Map.of("category", "IDEA", "message", "Many", "attachmentIds", four))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can add up to 3 screenshots"));
    }

    @Test
    void aPictureThatIsntOneOrTooManyUnsentIsRefused() throws Exception {
        User user = newUser();
        as(user, multipart("/user/attachments").file(new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This photo can't be read. Use a JPEG or PNG."));

        for (int i = 0; i < 10; i++) {
            upload(user, png(80, 80));
        }
        as(user, multipart("/user/attachments").file(file(png(80, 80))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unsentScreenshotsGoAfterADayAndAllGoWithTheAccount() throws Exception {
        User reporter = newUser();
        User reported = newUser();
        User other = newUser();
        makeFriends(reported, reporter);
        long postId = createPost(reported, "something");

        String old = upload(reporter, png(100, 100));
        String fresh = upload(reporter, png(100, 100));
        transactions.executeWithoutResult(status -> {
            Attachment attachment = attachments.findById(old).orElseThrow();
            attachment.setCreatedAt(Instant.now().minus(25, ChronoUnit.HOURS));
        });
        attachmentService.deleteUnsent();
        assertThat(attachments.findById(old)).isEmpty();
        assertThat(attachments.findById(fresh)).isPresent();

        // A report about the account that goes: its screenshots go with it, whoever uploaded them.
        as(reporter, post("/reports").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                "targetType", "USER", "targetId", reported.id(), "reason", "HARASSMENT", "attachmentIds", List.of(fresh)))))
                .andExpect(status().isOk());
        String othersOwn = upload(other, png(100, 100));
        deleteAccount(reported, PASSWORD).andExpect(status().isOk());
        assertThat(attachments.findById(fresh)).isEmpty();
        assertThat(attachments.findById(othersOwn)).isPresent();

        deleteAccount(other, PASSWORD).andExpect(status().isOk());
        assertThat(attachments.findById(othersOwn)).isEmpty();
        assertThat(postId).isPositive();
    }

    private String upload(User user, byte[] image) throws Exception {
        return json(as(user, multipart("/user/attachments").file(file(image))).andExpect(status().isOk())).get("id").asText();
    }

    private static MockMultipartFile file(byte[] image) {
        return new MockMultipartFile("file", "screenshot.png", "image/png", image);
    }

    private org.springframework.test.web.servlet.ResultActions sendFeedback(User user, Map<String, Object> body) throws Exception {
        return as(user, post("/user/feedback").contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(new HashMap<>(body))));
    }

    private static JsonNode mine(JsonNode feedback, User author) {
        for (JsonNode item : feedback) {
            if (author.name().equals(item.get("authorUsername").asText())) {
                return item;
            }
        }
        throw new AssertionError("No feedback from " + author.name());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** A PNG with something drawn on it, like a screenshot. */
    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("Lizma", 10, 20);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
