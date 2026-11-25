package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.model.ExtensionKey;
import com.example.lizma.model.Users;
import com.example.lizma.repo.ExtensionKeyRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Keys for the app's extensions (the home screen widget, the share extension). They can't use the user's session:
 * renewing it from an extension would race the app and sign it out. A key belongs to one sign-in on one phone and
 * stops working when that session ends (signing out, a suspension, the session running out); a sign-in has one
 * key per purpose.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionKeyService {

    /** last_used_at is written at most this often, not on every request. */
    private static final Duration TOUCH_EVERY = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExtensionKeyRepository keys;
    private final RefreshTokenService refreshTokens;
    private final UserRepository users;

    /**
     * A new key for the phone signed in with this refresh token. Asking again replaces that sign-in's previous key
     * of the same purpose. {@code noSessionMessage} is the error when the token isn't a live session of this user.
     */
    @Transactional
    public String issue(Users me, String refreshToken, ExtensionKey.Purpose purpose, String noSessionMessage) {
        RefreshTokenService.Session session = refreshTokens.liveSession(refreshToken)
                .filter(found -> found.userId().equals(me.getId()))
                .orElseThrow(() -> new InvalidActionException(noSessionMessage));
        String raw = newKey();
        ExtensionKey key = keys.findByFamilyIdAndPurpose(session.familyId(), purpose)
                .orElseGet(() -> ExtensionKey.builder()
                        .familyId(session.familyId())
                        .purpose(purpose)
                        .createdAt(Instant.now())
                        .build());
        key.setUser(users.getReferenceById(me.getId()));
        key.setKeyHash(hash(raw));
        keys.save(key);
        return raw;
    }

    /**
     * Whose key this is, if it's a live key of this purpose. A key whose sign-in has ended (or whose user was
     * suspended) is deleted on the way.
     */
    @Transactional
    public Optional<Users> authenticate(String rawKey, ExtensionKey.Purpose purpose) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        Optional<ExtensionKey> found = keys.findByKeyHash(hash(rawKey.strip()), purpose);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ExtensionKey key = found.get();
        Users user = key.getUser();
        if (user.isBanned() || !refreshTokens.isLive(key.getFamilyId())) {
            keys.delete(key);
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(TOUCH_EVERY))) {
            key.setLastUsedAt(now);
        }
        return Optional.of(user);
    }

    /** Of these users, those whose key of this purpose was used within this time. */
    @Transactional(readOnly = true)
    public List<Long> usersWithKeyUsed(ExtensionKey.Purpose purpose, Collection<Long> userIds, Duration within) {
        return userIds.isEmpty() ? List.of()
                : keys.findUsersWithKeyUsedSince(purpose, userIds, Instant.now().minus(within));
    }

    @Scheduled(cron = "${app.extension-keys.cleanup-cron:0 45 4 * * *}")
    @Transactional
    public void deleteOrphaned() {
        int deleted = keys.deleteOrphaned(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} extension key(s) whose sign-in has ended", deleted);
        }
    }

    private static String newKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
