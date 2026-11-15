package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.model.UserBlock;
import com.example.lizma.model.Users;
import com.example.lizma.repo.FriendRequestRepository;
import com.example.lizma.repo.FriendshipRepository;
import com.example.lizma.repo.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blocking (App Store guideline 1.2): a block cuts every connection between two users, in both directions. */
@Service
@RequiredArgsConstructor
public class BlockService {

    public static final String CANT_CONNECT_MESSAGE = "You can't connect with this user";

    private final UserBlockRepository blockRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserService userService;

    /** Blocks the user (idempotent) and removes the friendship and any requests between the two. */
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new InvalidActionException("You can't block yourself");
        }
        Users blocker = userService.getById(blockerId);
        Users blocked = userService.getById(blockedId);
        if (!blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            blockRepository.save(UserBlock.builder()
                    .blocker(blocker)
                    .blocked(blocked)
                    .createdAt(Instant.now())
                    .build());
        }
        friendshipRepository.deleteBetween(blocker, blocked);
        friendRequestRepository.deleteBetween(blocker, blocked);
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        blockRepository.deleteByIds(blockerId, blockedId);
    }

    /** Users this user has blocked, newest first. */
    @Transactional(readOnly = true)
    public List<Users> blockedBy(Long blockerId) {
        return blockRepository.findByBlockerOrderByCreatedAtDesc(userService.getById(blockerId)).stream()
                .map(UserBlock::getBlocked)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean blockedEitherWay(Long a, Long b) {
        return blockRepository.existsBetween(a, b);
    }

    /** Users whose content the viewer shouldn't see: the ones they blocked and the ones who blocked them. */
    @Transactional(readOnly = true)
    public Set<Long> hiddenUserIds(Long viewerId) {
        Set<Long> hidden = new HashSet<>(blockRepository.findBlockedIds(viewerId));
        hidden.addAll(blockRepository.findBlockerIds(viewerId));
        return hidden;
    }
}
