package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.FriendRequestAlreadyExistsException;
import com.example.lizma.exception.FriendRequestAlreadyProcessedException;
import com.example.lizma.exception.FriendRequestNotFoundException;
import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.UnauthorizedActionException;
import com.example.lizma.model.FriendRequest;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FriendRequestStatus;
import com.example.lizma.repo.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.lizma.model.enums.FriendRequestStatus.ACCEPTED;
import static com.example.lizma.model.enums.FriendRequestStatus.DECLINED;
import static com.example.lizma.model.enums.FriendRequestStatus.PENDING;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    public enum SendResult { SENT, BECAME_FRIENDS }

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipService friendshipService;
    private final UserService userService;

    @Transactional
    public SendResult sendFriendRequest(Long requesterId, Long recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new InvalidActionException("You can't send a friend request to yourself");
        }
        Users requester = userService.getById(requesterId);
        Users recipient = userService.getById(recipientId);

        if (friendshipService.areFriends(requester, recipient)) {
            throw new ConflictException("You are already friends");
        }
        // The other user has already asked us: accept that instead of creating a mirrored request.
        if (friendRequestRepository.findByRequesterAndRecipientAndStatus(recipient, requester, PENDING).isPresent()) {
            friendshipService.befriend(requester, recipient);
            return SendResult.BECAME_FRIENDS;
        }

        Optional<FriendRequest> existing = friendRequestRepository.findByRequesterAndRecipient(requester, recipient);
        if (existing.isPresent()) {
            FriendRequest request = existing.get();
            if (request.getStatus() == PENDING) {
                throw new FriendRequestAlreadyExistsException("Friend request already sent");
            }
            request.setStatus(PENDING); // sending again after it was declined
            return SendResult.SENT;
        }

        friendRequestRepository.save(FriendRequest.builder()
                .requester(requester)
                .recipient(recipient)
                .status(PENDING)
                .build());
        return SendResult.SENT;
    }

    @Transactional(readOnly = true)
    public List<FriendRequest> getReceivedFriendRequests(Long recipientId) {
        return friendRequestRepository.findByRecipientAndStatus(userService.getById(recipientId), PENDING);
    }

    /** Outgoing requests that are still waiting for an answer. */
    @Transactional(readOnly = true)
    public List<FriendRequest> getSentFriendRequests(Long requesterId) {
        return friendRequestRepository.findByRequesterAndStatus(userService.getById(requesterId), PENDING);
    }

    @Transactional
    public void respondToFriendRequest(Long requestId, String status, Long userId) {
        FriendRequestStatus answer = parseAnswer(status);
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException("Friend request not found"));

        if (!request.getRecipient().getId().equals(userId)) {
            throw new UnauthorizedActionException("You can only respond to requests sent to you");
        }
        if (request.getStatus() != PENDING) {
            throw new FriendRequestAlreadyProcessedException("This friend request has already been processed");
        }

        if (answer == ACCEPTED) {
            friendshipService.befriend(request.getRequester(), request.getRecipient()); // also marks the request accepted
        } else {
            request.setStatus(DECLINED);
        }
    }

    private static FriendRequestStatus parseAnswer(String status) {
        if ("ACCEPTED".equalsIgnoreCase(status)) {
            return ACCEPTED;
        }
        if ("DECLINED".equalsIgnoreCase(status)) {
            return DECLINED;
        }
        throw new InvalidActionException("Status must be ACCEPTED or DECLINED");
    }
}
