package com.example.lizma.service;

import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.Friendship;
import com.example.lizma.model.enums.FriendshipStatus;
import com.example.lizma.model.Users;
import com.example.lizma.repo.FriendshipRepository;
import com.example.lizma.repo.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public List<Friendship> getUserFriendships(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return friendshipRepository.findByUser1AndStatusOrUser2AndStatus(
                user, FriendshipStatus.CONFIRMED, user, FriendshipStatus.CONFIRMED);
    }

    @Transactional
    public void ensureFriendship(Long userAId, Long userBId) {
        if (userAId == null || userBId == null || userAId.equals(userBId)) return;

        Users a = userRepository.findById(userAId)
                .orElseThrow(() -> new UserNotFoundException("User A not found"));
        Users b = userRepository.findById(userBId)
                .orElseThrow(() -> new UserNotFoundException("User B not found"));

        Users u1 = a.getId() < b.getId() ? a : b;
        Users u2 = a.getId() < b.getId() ? b : a;

        boolean already =
                friendshipRepository.existsByUser1AndUser2AndStatus(u1, u2, FriendshipStatus.CONFIRMED);

        if (!already) {
            friendshipRepository.save(
                    Friendship.builder()
                            .user1(u1)
                            .user2(u2)
                            .status(FriendshipStatus.CONFIRMED)
                            .build()
            );
        }
    }
}
