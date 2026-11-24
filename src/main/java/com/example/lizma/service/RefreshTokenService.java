package com.example.lizma.service;

import com.example.lizma.model.RefreshToken;
import com.example.lizma.model.Users;
import com.example.lizma.repo.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens: issued at sign-in, traded for a new pair on every refresh (rotation), revoked at
 * sign-out, suspension and account deletion. Presenting a token that was already rotated means it was
 * copied: the whole session (family) is revoked, so neither the thief nor the user can go on with it.
 */
@Slf4j
@Service
public class RefreshTokenService {

    /** How long revoked tokens are kept to recognise their reuse. */
    private static final Duration KEEP_REVOKED = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${token.refresh-ttl:30d}") Duration ttl) {
        this.repository = repository;
        this.ttl = ttl;
    }

    /** A pair member for a new sign-in. Returns the raw token, which is never stored. */
    @Transactional
    public String issue(Users user) {
        return issue(user, UUID.randomUUID().toString());
    }

    public record Rotation(Users user, String refreshToken) {
    }

    /**
     * Trades a refresh token for a new one of the same session. Empty when the token is unknown, expired
     * or revoked (or the account is suspended); a revoked one also revokes its whole session.
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken.strip()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        Instant now = Instant.now();
        if (token.getRevokedAt() != null) {
            int revoked = repository.revokeFamily(token.getFamilyId(), now);
            log.warn("A rotated refresh token of {} was used again: revoked its session ({} tokens)",
                    token.getUser().getUsername(), revoked);
            return Optional.empty();
        }
        if (token.getExpiresAt().isBefore(now) || !token.getUser().isEnabled()) {
            return Optional.empty();
        }
        token.setRevokedAt(now);
        return Optional.of(new Rotation(token.getUser(), issue(token.getUser(), token.getFamilyId())));
    }

    /** Sign-out: ends the session the token belongs to and says whose it was. Unknown tokens are ignored. */
    @Transactional
    public Optional<Long> revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> token = repository.findByTokenHash(hash(rawToken.strip()));
        token.ifPresent(found -> repository.revokeFamily(found.getFamilyId(), Instant.now()));
        return token.map(found -> found.getUser().getId());
    }

    /** Every session of the user, e.g. on suspension. */
    @Transactional
    public void revokeAll(Users user) {
        repository.revokeAllOfUser(user.getId(), Instant.now());
    }

    @Scheduled(cron = "${token.cleanup-cron:0 30 4 * * *}")
    @Transactional
    public void deleteStale() {
        Instant now = Instant.now();
        int deleted = repository.deleteStale(now, now.minus(KEEP_REVOKED));
        if (deleted > 0) {
            log.info("Deleted {} expired or revoked refresh tokens", deleted);
        }
    }

    private String issue(Users user, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .build());
        return raw;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
