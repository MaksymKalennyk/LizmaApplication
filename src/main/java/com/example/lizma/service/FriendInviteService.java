package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.GoneException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.NotFoundException;
import com.example.lizma.model.FriendInvite;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.InviteLinkDto;
import com.example.lizma.repo.FriendInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendInviteService {

    public enum AcceptResult { BECAME_FRIENDS, ALREADY_FRIENDS }

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 random bytes = 256 bits, encoded as 43 base64url characters. */
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 64;

    private final FriendInviteRepository inviteRepository;
    private final FriendshipService friendshipService;
    private final UserService userService;
    private final ApplicationEventPublisher events;

    @Value("${app.externalBaseUrl}")
    private String externalBaseUrl;
    @Value("${app.invites.pathPrefix:/i}")
    private String pathPrefix;
    @Value("${app.invites.ttlHours:168}")
    private long ttlHours;

    /** Creates a new single-use invite link; every call returns a fresh token. */
    @Transactional
    public InviteLinkDto createInvite(Long requesterId) {
        Users requester = userService.getById(requesterId);
        Instant now = Instant.now();
        FriendInvite invite = inviteRepository.save(FriendInvite.builder()
                .token(newToken())
                .requester(requester)
                .status(FriendInvite.Status.ISSUED)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(ttlHours)))
                .build());
        return new InviteLinkDto(invite.getToken(), linkFor(invite.getToken()), invite.getExpiresAt().toString());
    }

    /**
     * Uses an invite: its creator and the user become friends and the invite is spent.
     * If they already are friends, nothing changes and the invite stays usable for someone else.
     * Accepting an invite you have already used is harmless and reports ALREADY_FRIENDS.
     */
    @Transactional
    public AcceptResult acceptInvite(String token, Long userId) {
        FriendInvite invite = lockInvite(token);
        Users user = userService.getById(userId);
        Users inviter = invite.getRequester();

        if (inviter.getId().equals(user.getId())) {
            throw new InvalidActionException("This is your own invite link");
        }
        if (invite.getStatus() == FriendInvite.Status.ACCEPTED) {
            if (invite.getAcceptedBy() != null && invite.getAcceptedBy().getId().equals(user.getId())) {
                return AcceptResult.ALREADY_FRIENDS;
            }
            throw new ConflictException("This invite link has already been used");
        }
        if (invite.isExpiredAt(Instant.now())) {
            throw new GoneException("This invite link has expired");
        }

        if (!friendshipService.befriend(inviter, user)) {
            return AcceptResult.ALREADY_FRIENDS;
        }
        invite.setStatus(FriendInvite.Status.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedBy(user);
        events.publishEvent(new ActivityEvent.BecameFriends(user.getId(), inviter.getId()));
        return AcceptResult.BECAME_FRIENDS;
    }

    /** Username of the inviter if the invite can still be used (shown on the public invite page). */
    @Transactional(readOnly = true)
    public Optional<String> usableInviteOwner(String token) {
        if (!looksLikeToken(token)) {
            return Optional.empty();
        }
        return inviteRepository.findByToken(token)
                .filter(invite -> invite.getStatus() == FriendInvite.Status.ISSUED)
                .filter(invite -> !invite.isExpiredAt(Instant.now()))
                .map(invite -> invite.getRequester().getUsername());
    }

    private FriendInvite lockInvite(String token) {
        if (!looksLikeToken(token)) {
            throw new NotFoundException("Invite link not found");
        }
        return inviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("Invite link not found"));
    }

    private String linkFor(String token) {
        return externalBaseUrl + pathPrefix + "/" + token;
    }

    private static boolean looksLikeToken(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
