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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestLinkRepository friendRequestLinkRepository;

    // Відправка запиту на дружбу
    public void sendFriendRequest(Long requesterId, Long recipientId) {
        if (requesterId.equals(recipientId)) {
            throw new InvalidActionException("Ви не можете відправити запит самому собі");
        }

        Users requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("Користувача-ініціатора не знайдено"));

        Users recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("Користувача-отримувача не знайдено"));

        // Перевірка на існуючий запит або дружбу
        if (friendRequestRepository.findByRequesterAndRecipient(requester, recipient).isPresent()) {
            throw new FriendRequestAlreadyExistsException("Запит на дружбу вже відправлено");
        }

        if (friendshipRepository.existsByUser1AndUser2AndStatus(requester, recipient, FriendshipStatus.CONFIRMED) ||
                friendshipRepository.existsByUser1AndUser2AndStatus(recipient, requester, FriendshipStatus.CONFIRMED)) {
            throw new InvalidActionException("Користувачі вже є друзями");
        }

        FriendRequest friendRequest = FriendRequest.builder()
                .requester(requester)
                .recipient(recipient)
                .status(FriendRequestStatus.PENDING)
                .build();

        friendRequestRepository.save(friendRequest);
    }

    // Отримання отриманих запитів
    public List<FriendRequest> getReceivedRequests(Long userId) {
        Users recipient = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));

        return friendRequestRepository.findByRecipientAndStatus(recipient, FriendRequestStatus.PENDING);
    }

    // Відповідь на запит
    public void respondToFriendRequest(Long requestId, FriendRequestStatus status, Long userId) {
        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new FriendRequestNotFoundException("Запит на дружбу не знайдено"));

        // Перевірка, чи поточний користувач є отримувачем запиту
        if (!friendRequest.getRecipient().getId().equals(userId)) {
            throw new UnauthorizedActionException("Ви не маєте права відповідати на цей запит");
        }

        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new FriendRequestAlreadyProcessedException("Запит на дружбу вже оброблено");
        }

        friendRequest.setStatus(status);
        friendRequestRepository.save(friendRequest);

        if (status == FriendRequestStatus.ACCEPTED) {
            // Створення дружби
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
                .orElseThrow(() -> new UserNotFoundException("Користувача-ініціатора не знайдено"));

        // Генерація унікального токена
        String token = UUID.randomUUID().toString();

        // Створення посилання
        FriendRequestLink link = FriendRequestLink.builder()
                .token(token)
                .requester(requester)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        friendRequestLinkRepository.save(link);

        // Повернення повного URL
        return "localhost:8080/user/friend-request/accept?token=" + token;
    }


    public String processFriendRequestLink(String token, Long recipientId) {
        FriendRequestLink link = friendRequestLinkRepository.findByToken(token)
                .orElseThrow(() -> new InvalidLinkException("Недійсне або прострочене посилання"));

        // Перевірка терміну дії посилання
        if (link.getExpiryDate() != null && link.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidLinkException("Посилання прострочене");
        }

        Users requester = link.getRequester();
        Users recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));

        // Перевірка на існуючу дружбу
        if (friendshipRepository.existsByUser1AndUser2AndStatus(requester, recipient, FriendshipStatus.CONFIRMED) ||
                friendshipRepository.existsByUser1AndUser2AndStatus(recipient, requester, FriendshipStatus.CONFIRMED)) {
            throw new InvalidActionException("Ви вже є друзями");
        }

        // Створення дружби
        Friendship friendship = Friendship.builder()
                .user1(requester)
                .user2(recipient)
                .status(FriendshipStatus.CONFIRMED)
                .build();

        friendshipRepository.save(friendship);

        // Видалення посилання
        friendRequestLinkRepository.delete(link);

        return "Ви тепер друзі!";
    }
}