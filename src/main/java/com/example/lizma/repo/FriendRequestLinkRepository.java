package com.example.lizma.repo;

import com.example.lizma.model.FriendRequestLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FriendRequestLinkRepository extends JpaRepository<FriendRequestLink, Long> {
    Optional<FriendRequestLink> findByToken(String token);
}
