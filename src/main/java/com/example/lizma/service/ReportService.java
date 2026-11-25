package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.NotFoundException;
import com.example.lizma.exception.TooManyRequestsException;
import com.example.lizma.model.Attachment;
import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ReportRequest;
import com.example.lizma.model.dto.ReportView;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.enums.ModerationActionType;
import com.example.lizma.model.enums.ReportAction;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Reports and moderation (App Store guideline 1.2): users report posts, comments and users;
 * moderators remove the content or suspend the author (which removes all of the author's content),
 * ideally within 24 hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** More reports than this per user and hour are refused, so nobody can flood the moderators. */
    static final int MAX_PER_HOUR = 30;
    private static final int HANDLED_LIMIT = 100;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostService postService;
    private final UserService userService;
    private final ContentCleanupService cleanupService;
    private final AccountService accountService;
    private final ModerationLogService moderationLog;
    private final ProfileService profileService;
    private final AttachmentService attachmentService;

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
        if (reportRepository.countByReporterAndCreatedAtAfter(reporter, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new TooManyRequestsException("You've sent a lot of reports. Please try again later.");
        }

        List<Attachment> screenshots = attachmentService.requireUnsent(reporter, request.getAttachmentIds());
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
        attachmentService.attach(screenshots, report);
        log.info("New report #{}: {} {} by {} ({}) needs moderation",
                report.getId(), type, targetId, reporter.getUsername(), request.getReason());
    }

    /**
     * Open reports oldest first (the queue); handled ones (RESOLVED, DISMISSED) most recently handled first,
     * the last {@value #HANDLED_LIMIT}.
     */
    @Transactional(readOnly = true)
    public List<ReportView> list(Collection<ReportStatus> statuses) {
        Set<ReportStatus> wanted = statuses == null || statuses.isEmpty() ? Set.of(ReportStatus.OPEN) : Set.copyOf(statuses);
        List<Report> reports = wanted.equals(Set.of(ReportStatus.OPEN))
                ? reportRepository.findByStatusOrderByCreatedAtAsc(ReportStatus.OPEN)
                : reportRepository.findByStatusInOrderByResolvedAtDesc(wanted, PageRequest.of(0, HANDLED_LIMIT));
        return reports.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return reportRepository.countByStatus(ReportStatus.OPEN);
    }

    /**
     * Applies a moderator's decision; every open report about the same target is closed with it.
     * BAN_USER closes every open report about that user.
     */
    @Transactional
    public void resolve(Long reportId, ReportAction action, Users moderator) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("This report has already been handled");
        }
        String targetName = report.getTargetUser() != null ? report.getTargetUser().getUsername() : null;

        switch (action) {
            case DISMISS -> {
                close(report, ReportStatus.DISMISSED, action, moderator);
                moderationLog.record(moderator, ModerationActionType.DISMISS_REPORT, targetName, report.getId());
            }
            case REMOVE_CONTENT -> {
                if (report.getTargetType() == ReportTargetType.USER) {
                    // What a user puts on show is their profile: photo and display name.
                    Users target = report.getTargetUser();
                    if (target == null) {
                        throw new NotFoundException("The reported user no longer exists");
                    }
                    profileService.removeProfile(target, moderator, report.getId());
                } else {
                    removeContent(report);
                    moderationLog.record(moderator, ModerationActionType.REMOVE_CONTENT, targetName, report.getId());
                }
                close(report, ReportStatus.RESOLVED, action, moderator);
            }
            case BAN_USER -> {
                Users target = report.getTargetUser();
                if (target == null) {
                    throw new NotFoundException("The reported user no longer exists");
                }
                // Removes everything the user posted (the reported item included) and closes
                // every open report about them, this one too.
                accountService.suspend(target, moderator, report.getId());
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

    private void close(Report report, ReportStatus status, ReportAction action, Users moderator) {
        Instant now = Instant.now();
        for (Report open : reportRepository.findByTargetTypeAndTargetIdAndStatus(
                report.getTargetType(), report.getTargetId(), ReportStatus.OPEN)) {
            open.setStatus(status);
            open.setResolvedAt(now);
            open.setResolution(action);
            open.setResolvedBy(moderator);
        }
    }

    /** Needs a transaction: it reads the reported post or comment. */
    public ReportView toView(Report report) {
        return new ReportView(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetUser() != null ? report.getTargetUser().getUsername() : null,
                preview(report),
                switch (report.getTargetType()) {
                    case POST -> postRepository.findById(report.getTargetId()).map(Post::getAlbumCoverUrl).orElse(null);
                    case USER -> report.getTargetUser() != null ? UserCard.avatarUrl(report.getTargetUser()) : null;
                    case COMMENT -> null;
                },
                report.getReason(),
                report.getDetails(),
                report.getReporter().getUsername(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt(),
                report.getResolution(),
                report.getResolvedBy() != null ? report.getResolvedBy().getUsername() : null,
                AttachmentService.urls(report.getAttachments()));
    }

    private String preview(Report report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(ReportService::describe)
                    .orElse(null);
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(PostComment::getContent)
                    .orElse(null);
            case USER -> report.getTargetUser() == null ? null
                    : report.getTargetUser().getDisplayName() != null
                    ? report.getTargetUser().getDisplayName() + " (@" + report.getTargetUser().getUsername() + ")"
                    : report.getTargetUser().getUsername();
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
