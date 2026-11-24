package com.example.lizma.service;

import com.example.lizma.exception.NotFoundException;
import com.example.lizma.exception.TooManyRequestsException;
import com.example.lizma.model.Attachment;
import com.example.lizma.model.Feedback;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FeedbackRequest;
import com.example.lizma.model.dto.FeedbackView;
import com.example.lizma.model.enums.FeedbackStatus;
import com.example.lizma.repo.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ideas, problems and other messages users send from the app. Moderators read them in the app and get
 * them by email (see {@link ModerationMailer}); an optional contact email lets them answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** More than this per user and hour is refused, so nobody can flood the inbox. */
    static final int MAX_PER_HOUR = 5;
    private static final int LIST_LIMIT = 200;

    private final FeedbackRepository repository;
    private final UserService userService;
    private final AttachmentService attachmentService;

    @Transactional
    public void submit(Long userId, FeedbackRequest request) {
        Users author = userService.getById(userId);
        if (repository.countByAuthorAndCreatedAtAfter(author, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new TooManyRequestsException("You've sent a lot of messages. Please try again in an hour.");
        }
        List<Attachment> screenshots = attachmentService.requireUnsent(author, request.attachmentIds());
        Feedback saved = repository.save(Feedback.builder()
                .author(author)
                .category(request.category())
                .message(request.message().strip())
                .contactEmail(blankToNull(request.contactEmail()))
                .appVersion(blankToNull(request.appVersion()))
                .osVersion(blankToNull(request.osVersion()))
                .status(FeedbackStatus.NEW)
                .createdAt(Instant.now())
                .build());
        attachmentService.attach(screenshots, saved);
        log.info("New feedback #{} ({}) from {}", saved.getId(), saved.getCategory(), author.getUsername());
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public List<FeedbackView> list(FeedbackStatus status) {
        return repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(FeedbackService::toView)
                .toList();
    }

    @Transactional
    public void markDone(Long id, Users moderator) {
        Feedback feedback = get(id);
        feedback.setStatus(FeedbackStatus.DONE);
        feedback.setHandledAt(Instant.now());
        feedback.setHandledBy(moderator);
    }

    @Transactional
    public void reopen(Long id) {
        Feedback feedback = get(id);
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setHandledAt(null);
        feedback.setHandledBy(null);
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return repository.countByStatus(FeedbackStatus.NEW);
    }

    static FeedbackView toView(Feedback feedback) {
        return new FeedbackView(
                feedback.getId(),
                feedback.getCategory(),
                feedback.getMessage(),
                feedback.getAuthor().getUsername(),
                feedback.getContactEmail(),
                feedback.getAppVersion(),
                feedback.getOsVersion(),
                feedback.getStatus(),
                feedback.getCreatedAt(),
                feedback.getHandledAt(),
                feedback.getHandledBy() != null ? feedback.getHandledBy().getUsername() : null,
                AttachmentService.urls(feedback.getAttachments()));
    }

    private Feedback get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Feedback not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
