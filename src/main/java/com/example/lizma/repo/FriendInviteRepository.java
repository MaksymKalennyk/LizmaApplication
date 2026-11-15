package com.example.lizma.repo;

import com.example.lizma.model.FriendInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {

    Optional<FriendInvite> findByToken(String token);

    /** Locks the invite row so a single-use invite can't be accepted twice at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FriendInvite i where i.token = :token")
    Optional<FriendInvite> findByTokenForUpdate(@Param("token") String token);
}
