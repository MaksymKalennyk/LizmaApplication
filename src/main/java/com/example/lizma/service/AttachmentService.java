package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.TooManyRequestsException;
import com.example.lizma.model.Attachment;
import com.example.lizma.model.Feedback;
import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.repo.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Screenshots for reports and feedback: uploaded one by one, then attached to what the user sends (up to
 * {@value #MAX_PER_ITEM}). Only moderators see them; unsent uploads are deleted after a day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    static final int MAX_PER_ITEM = 3;
    /** Uploaded and not sent yet, per user. */
    static final int MAX_UNSENT = 10;
    static final int MAX_PER_HOUR = 30;
    static final Duration KEEP_UNSENT = Duration.ofDays(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AttachmentRepository repository;
    private final UserService userService;

    /** Stores the picture as a screenshot of the user's (see {@link UploadedImages}); returns its id. */
    @Transactional
    public String upload(Users me, byte[] upload) {
        Users owner = userService.getById(me.getId());
        if (repository.countUnsent(owner) >= MAX_UNSENT
                || repository.countByOwnerAndCreatedAtAfter(owner, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new TooManyRequestsException("You've added a lot of screenshots. Please try again later.");
        }
        byte[] jpeg = UploadedImages.screenshot(upload);
        String token = newToken();
        repository.save(Attachment.builder().token(token).owner(owner).image(jpeg).createdAt(Instant.now()).build());
        return token;
    }

    /** Checks the ids before anything is saved: the user's own uploads, not sent yet, at most three. */
    List<Attachment> requireUnsent(Users owner, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> distinct = ids.stream().filter(id -> id != null && !id.isBlank()).map(String::strip).distinct().toList();
        if (distinct.size() > MAX_PER_ITEM) {
            throw new InvalidActionException("You can add up to 3 screenshots");
        }
        return distinct.stream()
                .map(id -> repository.findById(id)
                        .filter(attachment -> attachment.getOwner().getId().equals(owner.getId())
                                && attachment.getReport() == null && attachment.getFeedback() == null)
                        .orElseThrow(() -> new InvalidActionException("A screenshot couldn't be attached. Add it again.")))
                .toList();
    }

    void attach(List<Attachment> attachments, Report report) {
        attachments.forEach(attachment -> attachment.setReport(report));
    }

    void attach(List<Attachment> attachments, Feedback feedback) {
        attachments.forEach(attachment -> attachment.setFeedback(feedback));
    }

    /** Where moderators open them. */
    static List<String> urls(List<Attachment> attachments) {
        return attachments == null ? List.of()
                : attachments.stream().map(attachment -> "/admin/attachments/" + attachment.getToken() + ".jpg").toList();
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> image(String token) {
        return repository.findById(token).map(Attachment::getImage);
    }

    @Scheduled(cron = "${app.attachments.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void deleteUnsent() {
        int deleted = repository.deleteUnsentBefore(Instant.now().minus(KEEP_UNSENT));
        if (deleted > 0) {
            log.info("Deleted {} screenshot(s) that were never sent", deleted);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
