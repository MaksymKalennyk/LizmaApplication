package com.example.lizma.repo;

import com.example.lizma.model.Friendship;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUser1AndStatusOrUser2AndStatus(Users user1, FriendshipStatus status1, Users user2, FriendshipStatus status2);
    boolean existsByUser1AndUser2AndStatus(Users user1, Users user2, FriendshipStatus status);
}
