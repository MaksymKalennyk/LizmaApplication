package com.example.lizma.service;

import com.example.lizma.exception.*;
import com.example.lizma.model.*;
import com.example.lizma.model.enums.FriendRequestStatus;
import com.example.lizma.model.enums.FriendshipStatus;
import com.example.lizma.repo.FriendRequestLinkRepository;
import com.example.lizma.repo.FriendRequestRepository;
import com.example.lizma.repo.FriendshipRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestLinkRepository friendRequestLinkRepository;

    public void sendFriendRequest(Long requesterId, Long recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new InvalidActionException("You are not allowed to send friend request");
        }

        Users requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Users recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (friendRequestRepository.findByRequesterAndRecipient(requester, recipient).isPresent()) {
            throw new FriendRequestAlreadyExistsException("Request already exists");
        }

        if (friendshipRepository.existsByUser1AndUser2AndStatus(requester, recipient, FriendshipStatus.CONFIRMED) ||
                friendshipRepository.existsByUser1AndUser2AndStatus(recipient, requester, FriendshipStatus.CONFIRMED)) {
            throw new InvalidActionException("Users are already friends");
        }

        FriendRequest friendRequest = FriendRequest.builder()
                .requester(requester)
                .recipient(recipient)
                .status(FriendRequestStatus.PENDING)
                .build();

        friendRequestRepository.save(friendRequest);
    }

    public List<FriendRequest> getReceivedRequests(Long userId) {
        Users recipient = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return friendRequestRepository.findByRecipientAndStatus(recipient, FriendRequestStatus.PENDING);
    }

    public void respondToFriendRequest(Long requestId, FriendRequestStatus status, Long userId) {
        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException("Request not found"));

        if (!friendRequest.getRecipient().getId().equals(userId)) {
            throw new UnauthorizedActionException("You are not allowed to respond to friend request");
        }

        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new FriendRequestAlreadyProcessedException("Request is already pending");
        }

        friendRequest.setStatus(status);
        friendRequestRepository.save(friendRequest);

        if (status == FriendRequestStatus.ACCEPTED) {
            Friendship friendship = Friendship.builder()
                    .user1(friendRequest.getRequester())
                    .user2(friendRequest.getRecipient())
                    .status(FriendshipStatus.CONFIRMED)
                    .build();

            friendshipRepository.save(friendship);
        }
    }

    public String generateFriendRequestLink(Long requesterId) {
        Users requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String token = requester.getUsername();

        FriendRequestLink link = FriendRequestLink.builder()
                .token(token)
                .requester(requester)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        friendRequestLinkRepository.save(link);

        return "localhost:8080/user/friend-request/accept?token=" + token;
    }


    public String processFriendRequestLink(String token, Long recipientId) {
        FriendRequestLink link = friendRequestLinkRepository.findByToken(token)
                .orElseThrow(() -> new InvalidLinkException("Expired link"));

        if (link.getExpiryDate() != null && link.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidLinkException("Expired link");
        }

        Users requester = link.getRequester();
        Users recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (friendshipRepository.existsByUser1AndUser2AndStatus(requester, recipient, FriendshipStatus.CONFIRMED) ||
                friendshipRepository.existsByUser1AndUser2AndStatus(recipient, requester, FriendshipStatus.CONFIRMED)) {
            throw new InvalidActionException("You are already friend");
        }

        Friendship friendship = Friendship.builder()
                .user1(requester)
                .user2(recipient)
                .status(FriendshipStatus.CONFIRMED)
                .build();

        friendshipRepository.save(friendship);

        friendRequestLinkRepository.delete(link);

        return "Ви тепер друзі!";
    }
}