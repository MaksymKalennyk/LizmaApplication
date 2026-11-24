package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.model.Post;
import com.example.lizma.model.Users;
import com.example.lizma.model.WidgetKey;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.dto.WidgetFeed;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.UserRepository;
import com.example.lizma.repo.WidgetKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
 * The home screen widget: friends' latest songs, read with a key of the widget's own. The key belongs to the
 * sign-in on that phone and dies with it, so signing out (or a suspension) also empties the widget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetService {

    /** As many as the largest widget shows. */
    static final int FEED_SIZE = 4;
    /** last_used_at is written at most this often, not on every fetch. */
    private static final Duration TOUCH_EVERY = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WidgetKeyRepository keys;
    private final RefreshTokenService refreshTokens;
    private final UserRepository users;
    private final FriendshipService friendshipService;
    private final PostRepository postRepository;

    /**
     * A key for the widget of the phone signed in with this refresh token. Asking again replaces that sign-in's
     * previous key.
     */
    @Transactional
    public String issue(Users me, String refreshToken) {
        RefreshTokenService.Session session = refreshTokens.liveSession(refreshToken)
                .filter(found -> found.userId().equals(me.getId()))
                .orElseThrow(() -> new InvalidActionException("Sign in again to set up the widget"));
        String raw = newKey();
        WidgetKey key = keys.findByFamilyId(session.familyId())
                .orElseGet(() -> WidgetKey.builder().familyId(session.familyId()).createdAt(Instant.now()).build());
        key.setUser(users.getReferenceById(me.getId()));
        key.setKeyHash(hash(raw));
        keys.save(key);
        return raw;
    }

    /** Friends' latest songs, newest first; empty when the key is unknown or its sign-in has ended. */
    @Transactional
    public Optional<WidgetFeed> feed(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        Optional<WidgetKey> found = keys.findByKeyHash(hash(rawKey.strip()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        WidgetKey key = found.get();
        Users user = key.getUser();
        if (user.isBanned() || !refreshTokens.isLive(key.getFamilyId())) {
            keys.delete(key);
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(TOUCH_EVERY))) {
            key.setLastUsedAt(now);
        }
        List<Long> friendIds = friendshipService.getFriendIds(user);
        if (friendIds.isEmpty()) {
            return Optional.of(new WidgetFeed(List.of()));
        }
        List<WidgetFeed.Post> posts = postRepository.findFeedPage(friendIds, PageRequest.of(0, FEED_SIZE)).stream()
                .map(WidgetService::toWidgetPost)
                .toList();
        return Optional.of(new WidgetFeed(posts));
    }

    /** Of these users, those whose widget has fetched lately, i.e. it's on their screen. */
    @Transactional(readOnly = true)
    public List<Long> usersWithWidget(Collection<Long> userIds, Duration within) {
        return userIds.isEmpty() ? List.of() : keys.findUsersWithWidgetSince(userIds, Instant.now().minus(within));
    }

    @Scheduled(cron = "${app.widgets.cleanup-cron:0 45 4 * * *}")
    @Transactional
    public void deleteOrphaned() {
        int deleted = keys.deleteOrphaned(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} widget key(s) whose sign-in has ended", deleted);
        }
    }

    private static WidgetFeed.Post toWidgetPost(Post post) {
        Users author = post.getAuthor();
        String name = author.getDisplayName() != null && !author.getDisplayName().isBlank()
                ? author.getDisplayName() : author.getUsername();
        return new WidgetFeed.Post(post.getId(), author.getUsername(), name, UserCard.avatarUrl(author),
                post.getTrackName(), post.getArtistName(), post.getAlbumCoverUrl(), post.getCreatedAt());
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
