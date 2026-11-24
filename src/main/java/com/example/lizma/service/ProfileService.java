package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.UnauthorizedActionException;
import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.UserAvatar;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ProfileView;
import com.example.lizma.model.dto.ProfileView.Relationship;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.enums.FriendRequestStatus;
import com.example.lizma.model.enums.ModerationActionType;
import com.example.lizma.repo.FriendRequestRepository;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.UserAvatarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Profiles: a display name and a photo next to the username, and who may see what. Name and photo are
 * public to everyone who can find the user; their posts stay with their friends, as in the feed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final FriendshipService friendshipService;
    private final BlockService blockService;
    private final FriendRequestRepository friendRequestRepository;
    private final PostRepository postRepository;
    private final UserAvatarRepository avatarRepository;
    private final ContentFilter contentFilter;
    private final ModerationLogService moderationLog;

    /** Suspended users and users blocked either way don't exist for the viewer (404). */
    @Transactional(readOnly = true)
    public ProfileView getProfile(Users viewer, String username) {
        Users user = visibleUser(viewer, username);
        Relationship relationship;
        Long requestId = null;
        if (user.getId().equals(viewer.getId())) {
            relationship = Relationship.SELF;
        } else if (friendshipService.areFriends(viewer, user)) {
            relationship = Relationship.FRIEND;
        } else {
            var sent = friendRequestRepository.findByRequesterAndRecipientAndStatus(viewer, user, FriendRequestStatus.PENDING);
            var received = friendRequestRepository.findByRequesterAndRecipientAndStatus(user, viewer, FriendRequestStatus.PENDING);
            if (sent.isPresent()) {
                relationship = Relationship.REQUEST_SENT;
                requestId = sent.get().getId();
            } else if (received.isPresent()) {
                relationship = Relationship.REQUEST_RECEIVED;
                requestId = received.get().getId();
            } else {
                relationship = Relationship.NONE;
            }
        }
        boolean canSeePosts = relationship == Relationship.SELF || relationship == Relationship.FRIEND;
        UserCard card = UserCard.of(user);
        return new ProfileView(card.id(), card.username(), card.displayName(), card.avatarUrl(), relationship, requestId,
                canSeePosts, canSeePosts ? postRepository.countByAuthor(user) : null);
    }

    /** The user whose posts the viewer may see, else 404 (unknown, hidden) or 403 (not friends). */
    @Transactional(readOnly = true)
    public Users requirePostsVisible(Users viewer, String username) {
        Users user = visibleUser(viewer, username);
        if (!user.getId().equals(viewer.getId()) && !friendshipService.areFriends(viewer, user)) {
            throw new UnauthorizedActionException("Only friends can see these posts");
        }
        return user;
    }

    @Transactional
    public ProfileView setDisplayName(Users me, String displayName) {
        Users user = userService.getById(me.getId());
        String name = displayName == null ? null : displayName.strip().replaceAll("\\s+", " ");
        if (name != null && name.isEmpty()) {
            name = null;
        }
        if (name != null) {
            if (name.codePoints().anyMatch(Character::isISOControl)) {
                throw new InvalidActionException("The name can't contain line breaks or control characters");
            }
            contentFilter.check(name);
        }
        user.setDisplayName(name);
        return getProfile(user, user.getUsername());
    }

    /** Stores a new photo (see {@link UploadedImages}); the old one is deleted and its URL stops working. */
    @Transactional
    public String setAvatar(Users me, byte[] upload) {
        byte[] jpeg = UploadedImages.avatar(upload);
        Users user = userService.getById(me.getId());
        avatarRepository.deleteByUser(user);
        avatarRepository.flush();
        String token = newToken();
        avatarRepository.save(UserAvatar.builder().token(token).user(user).image(jpeg).createdAt(Instant.now()).build());
        user.setAvatarToken(token);
        return UserCard.avatarUrl(user);
    }

    @Transactional
    public void removeAvatar(Users me) {
        Users user = userService.getById(me.getId());
        avatarRepository.deleteByUser(user);
        user.setAvatarToken(null);
    }

    /** A moderator takes down a user's photo and name (e.g. on a report about the profile). */
    @Transactional
    public void removeProfile(Users user, Users moderator, Long reportId) {
        avatarRepository.deleteByUser(user);
        user.setAvatarToken(null);
        user.setDisplayName(null);
        moderationLog.record(moderator, ModerationActionType.REMOVE_PROFILE, user.getUsername(), reportId);
        log.warn("Profile photo and name of {} removed by {}", user.getUsername(), moderator.getUsername());
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> avatarImage(String token) {
        return avatarRepository.findById(token).map(UserAvatar::getImage);
    }

    private Users visibleUser(Users viewer, String username) {
        Users user = userService.getByUsername(username.strip());
        if (user.isBanned() || blockService.blockedEitherWay(viewer.getId(), user.getId())) {
            throw new UserNotFoundException("User not found");
        }
        return user;
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
