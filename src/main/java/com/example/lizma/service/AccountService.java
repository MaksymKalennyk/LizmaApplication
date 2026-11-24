package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.ConflictException;
import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.ModerationActionType;
import com.example.lizma.model.enums.ReportAction;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.Role;
import com.example.lizma.repo.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The life of an account after sign-up: in-app deletion (App Store guideline 5.1.1(v)) and
 * suspension by moderators (guideline 1.2: abusive users are removed from the service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ContentCleanupService cleanupService;
    private final ReportRepository reportRepository;
    private final ModerationLogService moderationLog;
    private final RefreshTokenService refreshTokens;

    /**
     * Permanently deletes the account and all of its data after checking the password.
     * A wrong password is a 400, not a 401: the app treats 401 as "session expired".
     * <p>
     * A moderator's account can't be deleted this way: its username is in the server's admin list, and a
     * freed username could be taken by someone else, who would then become a moderator.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        Users user = userService.getById(userId);
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidActionException("Incorrect password");
        }
        if (user.getRole() == Role.ROLE_ADMIN) {
            throw new ConflictException("A moderator account can't be deleted in the app. "
                    + "Remove it from the server's admin list first.");
        }
        cleanupService.deleteUser(userId);
    }

    /**
     * Suspends the account: it can't sign in, its tokens stop working, and everything it posted disappears
     * together with its friendships, so nobody keeps seeing a suspended user's content. The account row
     * stays, which keeps the username taken and lets sign-in answer "suspended". Every open report about
     * the user is closed as resolved by this moderator. Suspending an already suspended account is harmless.
     *
     * @param reportId the report that led to it, for the moderation log; null for a direct ban
     */
    @Transactional
    public void suspend(Users user, Users moderator, Long reportId) {
        requireNotModerator(user, "suspended");
        user.setBanned(true);
        refreshTokens.revokeAll(user);
        cleanupService.removeUserContent(user.getId());

        Instant now = Instant.now();
        for (Report open : reportRepository.findByTargetUserAndStatus(user, ReportStatus.OPEN)) {
            open.setStatus(ReportStatus.RESOLVED);
            open.setResolvedAt(now);
            open.setResolution(ReportAction.BAN_USER);
            open.setResolvedBy(moderator);
        }
        moderationLog.record(moderator, ModerationActionType.BAN_USER, user.getUsername(), reportId);
        log.warn("User {} suspended by {}, their content and connections were removed",
                user.getUsername(), moderator.getUsername());
    }

    @Transactional
    public void suspend(String username, Users moderator) {
        suspend(userService.getByUsername(username), moderator, null);
    }

    /** Lifts a suspension. What was removed when the account was suspended does not come back. */
    @Transactional
    public void reinstate(String username, Users moderator) {
        Users user = userService.getByUsername(username);
        if (user.isBanned()) {
            user.setBanned(false);
            moderationLog.record(moderator, ModerationActionType.UNBAN_USER, user.getUsername(), null);
            log.warn("User {} reinstated by {}", username, moderator.getUsername());
        }
    }

    /** Deletes an account on a moderator's decision, e.g. a deletion request from a user who can't sign in. */
    @Transactional
    public void deleteByModerator(String username, Users moderator) {
        Users user = userService.getByUsername(username);
        requireNotModerator(user, "deleted by another moderator");
        cleanupService.deleteUser(user.getId());
        moderationLog.record(moderator, ModerationActionType.DELETE_USER, user.getUsername(), null);
        log.warn("Account {} deleted by {}", username, moderator.getUsername());
    }

    private static void requireNotModerator(Users user, String action) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            throw new InvalidActionException("A moderator's account can't be " + action);
        }
    }
}
