package com.example.lizma.service;

import com.example.lizma.model.Feedback;
import com.example.lizma.model.Report;
import com.example.lizma.model.dto.FeedbackView;
import com.example.lizma.model.dto.ReportView;
import com.example.lizma.model.enums.FeedbackStatus;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.repo.FeedbackRepository;
import com.example.lizma.repo.ReportRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tells the moderators by email about new reports and feedback. Every few minutes everything that hasn't
 * been mailed yet goes out in one digest, so a burst of reports is one email, not a hundred; feedback that
 * left a contact email gets a mail of its own with Reply-To set, so answering is one click in the mailbox.
 * <p>
 * The database is the source of truth: an item is marked as mailed only after its mail was accepted, so
 * when the mail server is down nothing is lost, it goes out on a later run. Without {@code spring.mail.host}
 * (MAIL_HOST in .env) nothing is sent and the moderators see everything in the app only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationMailer {

    /** Items per digest; anything beyond waits for the next run. */
    private static final int DIGEST_LIMIT = 50;
    /** Separate mails (feedback with a contact email) per run. */
    private static final int PERSONAL_LIMIT = 10;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final ReportRepository reportRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportService reportService;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TransactionTemplate transactions;

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.username:}")
    private String from;
    @Value("${app.notify.to:}")
    private String to;

    @PostConstruct
    void announce() {
        if (enabled()) {
            log.info("Moderation emails go to {}", to);
        } else {
            log.info("Moderation emails are off: set MAIL_HOST, MAIL_USERNAME and MAIL_PASSWORD in .env to turn them on");
        }
    }

    public boolean enabled() {
        return StringUtils.hasText(mailHost) && StringUtils.hasText(to) && mailSender.getIfAvailable() != null;
    }

    @Scheduled(fixedDelayString = "${app.notify.interval:PT5M}", initialDelayString = "${app.notify.initial-delay:PT1M}")
    public void sendPending() {
        if (enabled()) {
            sendPendingNow();
        }
    }

    /** One run: the digest, then the separate feedback mails. Returns how many mails went out. */
    public int sendPendingNow() {
        Pending pending = transactions.execute(status -> loadPending());
        if (pending == null) {
            return 0;
        }
        // Already handled in the app before their mail went out: nothing left to tell.
        if (!pending.handledReportIds().isEmpty() || !pending.handledFeedbackIds().isEmpty()) {
            markNotified(pending.handledReportIds(), pending.handledFeedbackIds());
        }
        int sent = 0;
        if (!pending.reports().isEmpty() || !pending.feedback().isEmpty()) {
            SimpleMailMessage digest = message(digestSubject(pending.reports().size(), pending.feedback().size()),
                    digestBody(pending.reports(), pending.feedback()));
            if (send(digest)) {
                markNotified(pending.reports().stream().map(ReportView::id).toList(),
                        pending.feedback().stream().map(FeedbackView::id).toList());
                sent++;
            } else {
                return sent;
            }
        }
        for (FeedbackView feedback : pending.personal()) {
            SimpleMailMessage mail = message("[Lizma] " + categoryTitle(feedback) + " from @" + feedback.authorUsername(),
                    personalBody(feedback));
            mail.setReplyTo(feedback.contactEmail());
            if (!send(mail)) {
                break;
            }
            markNotified(List.of(), List.of(feedback.id()));
            sent++;
        }
        return sent;
    }

    private record Pending(List<ReportView> reports, List<FeedbackView> feedback, List<FeedbackView> personal,
                           List<Long> handledReportIds, List<Long> handledFeedbackIds) {
    }

    /** Not mailed yet; what moderators already handled in the app is set apart, it isn't news anymore. */
    private Pending loadPending() {
        List<ReportView> reports = new ArrayList<>();
        List<Long> handledReports = new ArrayList<>();
        for (Report report : reportRepository.findByNotifiedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, DIGEST_LIMIT))) {
            if (report.getStatus() == ReportStatus.OPEN) {
                reports.add(reportService.toView(report));
            } else {
                handledReports.add(report.getId());
            }
        }
        List<FeedbackView> digestFeedback = new ArrayList<>();
        List<FeedbackView> personal = new ArrayList<>();
        List<Long> handledFeedback = new ArrayList<>();
        for (Feedback feedback : feedbackRepository.findByNotifiedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, DIGEST_LIMIT))) {
            FeedbackView view = FeedbackService.toView(feedback);
            if (view.status() != FeedbackStatus.NEW) {
                handledFeedback.add(view.id());
            } else if (view.contactEmail() == null) {
                digestFeedback.add(view);
            } else if (personal.size() < PERSONAL_LIMIT) {
                personal.add(view);
            }
        }
        return new Pending(reports, digestFeedback, personal, handledReports, handledFeedback);
    }

    private void markNotified(List<Long> reportIds, List<Long> feedbackIds) {
        Instant now = Instant.now();
        transactions.executeWithoutResult(status -> {
            reportRepository.findAllById(reportIds).forEach(report -> report.setNotifiedAt(now));
            feedbackRepository.findAllById(feedbackIds).forEach(feedback -> feedback.setNotifiedAt(now));
        });
    }

    private boolean send(SimpleMailMessage mail) {
        try {
            mailSender.getObject().send(mail);
            return true;
        } catch (MailException e) {
            log.warn("Couldn't send the moderation email, will retry on the next run: {}", e.getMessage());
            return false;
        }
    }

    private SimpleMailMessage message(String subject, String body) {
        SimpleMailMessage mail = new SimpleMailMessage();
        if (StringUtils.hasText(from)) {
            mail.setFrom(from);
        }
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(body);
        return mail;
    }

    // ---------------------------------------------------------------- texts (for the moderators, in English)

    static String digestSubject(int reports, int feedback) {
        List<String> parts = new ArrayList<>();
        if (reports > 0) {
            parts.add(reports + (reports == 1 ? " new report" : " new reports"));
        }
        if (feedback > 0) {
            parts.add(feedback + " new feedback");
        }
        return "[Lizma] " + String.join(", ", parts);
    }

    private static String digestBody(List<ReportView> reports, List<FeedbackView> feedback) {
        StringBuilder body = new StringBuilder();
        if (!reports.isEmpty()) {
            body.append("NEW REPORTS (").append(reports.size()).append(")\n\n");
            for (ReportView report : reports) {
                body.append("#").append(report.id()).append("  ").append(describeTarget(report))
                        .append("  ·  ").append(report.reason()).append('\n');
                body.append("  ").append(report.targetPreview() != null
                        ? quote(report.targetPreview()) : "(already deleted)").append('\n');
                if (report.details() != null) {
                    body.append("  Details: ").append(quote(report.details())).append('\n');
                }
                body.append(screenshots(report.attachments()));
                body.append("  Reported by @").append(report.reporterUsername())
                        .append(" on ").append(TIME.format(report.createdAt())).append("\n\n");
            }
        }
        if (!feedback.isEmpty()) {
            body.append("FEEDBACK (").append(feedback.size()).append(")\n\n");
            for (FeedbackView item : feedback) {
                body.append("#").append(item.id()).append("  ").append(categoryTitle(item))
                        .append(" from @").append(item.authorUsername())
                        .append("  ·  ").append(TIME.format(item.createdAt())).append(versions(item)).append('\n');
                body.append(indent(item.message())).append('\n');
                body.append(screenshots(item.attachments())).append('\n');
            }
        }
        body.append("Handle them in the app: Profile → Moderation.\n");
        return body.toString();
    }

    private static String personalBody(FeedbackView feedback) {
        return categoryTitle(feedback) + " from @" + feedback.authorUsername()
                + "  ·  " + TIME.format(feedback.createdAt()) + versions(feedback) + "\n\n"
                + feedback.message() + "\n\n"
                + screenshots(feedback.attachments()).strip() + (feedback.attachments().isEmpty() ? "" : "\n\n")
                + "Reply to this email to answer them at " + feedback.contactEmail() + ".\n"
                + "Mark it as done in the app: Profile → Moderation → Feedback.\n";
    }

    /** Screenshots stay on the server (they may show someone's private screen): the mail only says there are some. */
    private static String screenshots(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        int count = attachments.size();
        return "  " + (count == 1 ? "1 screenshot: open it" : count + " screenshots: open them") + " in the app, Profile → Moderation\n";
    }

    private static String describeTarget(ReportView report) {
        String who = report.targetUsername() != null ? "@" + report.targetUsername() : "a deleted user";
        return switch (report.targetType()) {
            case POST -> "Post by " + who;
            case COMMENT -> "Comment by " + who;
            case USER -> "User " + who;
        };
    }

    private static String categoryTitle(FeedbackView feedback) {
        return switch (feedback.category()) {
            case IDEA -> "Idea";
            case PROBLEM -> "Problem";
            case OTHER -> "Message";
        };
    }

    private static String versions(FeedbackView feedback) {
        if (feedback.appVersion() == null && feedback.osVersion() == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (feedback.appVersion() != null) {
            parts.add("Lizma " + feedback.appVersion());
        }
        if (feedback.osVersion() != null) {
            parts.add(feedback.osVersion());
        }
        return "  ·  " + String.join(", ", parts);
    }

    private static String quote(String text) {
        return "“" + text.strip() + "”";
    }

    private static String indent(String text) {
        return "  " + text.strip().replace("\n", "\n  ");
    }
}
