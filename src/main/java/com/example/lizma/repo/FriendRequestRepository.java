package com.example.lizma.repo;

import com.example.lizma.model.FriendRequest;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    List<FriendRequest> findByRecipientAndStatus(Users recipient, FriendRequestStatus status);
    Optional<FriendRequest> findByRequesterAndRecipient(Users requester, Users recipient);
}
