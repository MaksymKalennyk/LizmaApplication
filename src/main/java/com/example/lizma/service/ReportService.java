package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.NotFoundException;
import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ReportRequest;
import com.example.lizma.model.dto.ReportView;
import com.example.lizma.model.enums.ReportAction;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reports and moderation (App Store guideline 1.2): users report posts, comments and users;
 * moderators remove the content or suspend the author (which removes all of the author's content),
 * ideally within 24 hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostService postService;
    private final UserService userService;
    private final ContentCleanupService cleanupService;
    private final AccountService accountService;

    /** Files a report; reporting the same thing again while it's still open is a no-op. */
    @Transactional
    public void report(Long reporterId, ReportRequest request) {
        Users reporter = userService.getById(reporterId);
        ReportTargetType type = request.getTargetType();
        Long targetId = request.getTargetId();

        Users targetUser = switch (type) {
            case POST -> postService.requireVisiblePost(targetId, reporter).getAuthor();
            case COMMENT -> {
                PostComment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Comment not found"));
                postService.requireVisiblePost(comment.getPost().getId(), reporter);
                yield comment.getAuthor();
            }
            case USER -> userService.getById(targetId);
        };
        if (targetUser.getId().equals(reporterId)) {
            throw new InvalidActionException(type == ReportTargetType.USER
                    ? "You can't report yourself"
                    : "You can't report your own content");
        }
        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(reporter, type, targetId, ReportStatus.OPEN)) {
            return;
        }

        Report report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(type)
                .targetId(targetId)
                .targetUser(targetUser)
                .reason(request.getReason())
                .details(blankToNull(request.getDetails()))
                .status(ReportStatus.OPEN)
                .createdAt(Instant.now())
                .build());
        log.warn("New report #{}: {} {} by {} ({}) needs moderation",
                report.getId(), type, targetId, reporter.getUsername(), request.getReason());
    }

    @Transactional(readOnly = true)
    public List<ReportView> list(ReportStatus status) {
        return reportRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Applies a moderator's decision; every open report about the same target is closed with it.
     * BAN_USER closes every open report about that user.
     */
    @Transactional
    public void resolve(Long reportId, ReportAction action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("This report has already been handled");
        }

        switch (action) {
            case DISMISS -> close(report, ReportStatus.DISMISSED);
            case REMOVE_CONTENT -> {
                if (report.getTargetType() == ReportTargetType.USER) {
                    throw new InvalidActionException("A user report has no content to remove: use BAN_USER or DISMISS");
                }
                removeContent(report);
                close(report, ReportStatus.RESOLVED);
            }
            case BAN_USER -> {
                Users target = report.getTargetUser();
                if (target == null) {
                    throw new NotFoundException("The reported user no longer exists");
                }
                // Removes everything the user posted (the reported item included) and closes
                // every open report about them, this one too.
                accountService.suspend(target);
                log.warn("User {} suspended on report #{}", target.getUsername(), report.getId());
            }
        }
    }

    private void removeContent(Report report) {
        switch (report.getTargetType()) {
            case POST -> cleanupService.deletePost(report.getTargetId());
            case COMMENT -> cleanupService.deleteComment(report.getTargetId());
            case USER -> { /* nothing to remove */ }
        }
    }

    private void close(Report report, ReportStatus status) {
        Instant now = Instant.now();
        for (Report open : reportRepository.findByTargetTypeAndTargetIdAndStatus(
                report.getTargetType(), report.getTargetId(), ReportStatus.OPEN)) {
            open.setStatus(status);
            open.setResolvedAt(now);
        }
    }

    private ReportView toView(Report report) {
        return new ReportView(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetUser() != null ? report.getTargetUser().getUsername() : null,
                preview(report),
                report.getReason(),
                report.getDetails(),
                report.getReporter().getUsername(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt());
    }

    private String preview(Report report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(ReportService::describe)
                    .orElse(null);
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(PostComment::getContent)
                    .orElse(null);
            case USER -> report.getTargetUser() != null ? report.getTargetUser().getUsername() : null;
        };
    }

    private static String describe(Post post) {
        String track = post.getTrackName() + " — " + post.getArtistName();
        return post.getCaption() == null ? track : post.getCaption() + " (" + track + ")";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
