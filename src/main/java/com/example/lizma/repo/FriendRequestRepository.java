package com.example.lizma.repo;

import com.example.lizma.model.FriendRequest;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByRecipientAndStatus(Users recipient, FriendRequestStatus status);

    List<FriendRequest> findByRequesterAndStatus(Users requester, FriendRequestStatus status);

    Optional<FriendRequest> findByRequesterAndRecipient(Users requester, Users recipient);

    Optional<FriendRequest> findByRequesterAndRecipientAndStatus(Users requester, Users recipient, FriendRequestStatus status);

    /** Pending requests between two users, in both directions. */
    @Query("""
            select r from FriendRequest r
            where r.status = com.example.lizma.model.enums.FriendRequestStatus.PENDING
              and ((r.requester = :a and r.recipient = :b) or (r.requester = :b and r.recipient = :a))
            """)
    List<FriendRequest> findPendingBetween(@Param("a") Users a, @Param("b") Users b);

    @Modifying
    @Query("delete from FriendRequest r where (r.requester = :a and r.recipient = :b) or (r.requester = :b and r.recipient = :a)")
    void deleteBetween(@Param("a") Users a, @Param("b") Users b);
}
