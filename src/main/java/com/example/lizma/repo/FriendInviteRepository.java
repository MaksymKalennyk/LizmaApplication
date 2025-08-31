package com.example.lizma.repo;

import com.example.lizma.model.FriendInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {
    Optional<FriendInvite> findByToken(String token);
}
