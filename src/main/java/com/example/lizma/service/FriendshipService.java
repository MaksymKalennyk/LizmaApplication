package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.UnauthorizedActionException;
import com.example.lizma.model.Friendship;
import com.example.lizma.model.Users;
import com.example.lizma.repo.FriendRequestRepository;
import com.example.lizma.repo.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.lizma.model.enums.FriendRequestStatus.ACCEPTED;
import static com.example.lizma.model.enums.FriendshipStatus.CONFIRMED;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserService userService;
    private final BlockService blockService;

    @Transactional(readOnly = true)
    public List<Friendship> getUserFriendships(Long userId) {
        Users user = userService.getById(userId);
        return friendshipRepository.findByUser1AndStatusOrUser2AndStatus(user, CONFIRMED, user, CONFIRMED);
    }

    /** The other side of every confirmed friendship of the user. */
    @Transactional(readOnly = true)
    public List<Users> getFriends(Users user) {
        return friendshipRepository.findByUser1AndStatusOrUser2AndStatus(user, CONFIRMED, user, CONFIRMED).stream()
                .map(friendship -> friendship.getUser1().getId().equals(user.getId())
                        ? friendship.getUser2()
                        : friendship.getUser1())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Users a, Users b) {
        return friendshipRepository.existsBetween(a, b, CONFIRMED);
    }

    /**
     * Makes two users friends; safe to call repeatedly. The pair is stored with the smaller id first
     * (a unique index then rules out duplicates) and pending requests between them are marked accepted.
     *
     * @return true if a new friendship was created, false if they already were friends
     */
    @Transactional
    public boolean befriend(Users a, Users b) {
        if (a.getId().equals(b.getId())) {
            throw new InvalidActionException("You can't add yourself as a friend");
        }
        if (blockService.blockedEitherWay(a.getId(), b.getId())) {
            throw new UnauthorizedActionException(BlockService.CANT_CONNECT_MESSAGE);
        }
        friendRequestRepository.findPendingBetween(a, b).forEach(request -> request.setStatus(ACCEPTED));
        if (areFriends(a, b)) {
            return false;
        }
        boolean aFirst = a.getId() < b.getId();
        friendshipRepository.save(Friendship.builder()
                .user1(aFirst ? a : b)
                .user2(aFirst ? b : a)
                .status(CONFIRMED)
                .build());
        return true;
    }
}
