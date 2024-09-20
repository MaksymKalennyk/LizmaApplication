package com.example.lizma.service;

import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.Friendship;
import com.example.lizma.model.enums.FriendshipStatus;
import com.example.lizma.model.Users;
import com.example.lizma.repo.FriendshipRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    // Отримання списку друзів користувача
    public List<Friendship> getUserFriendships(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));

        return friendshipRepository.findByUser1AndStatusOrUser2AndStatus(
                user, FriendshipStatus.CONFIRMED, user, FriendshipStatus.CONFIRMED);
    }
}
