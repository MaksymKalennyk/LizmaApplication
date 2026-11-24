package com.example.lizma.repo;

import com.example.lizma.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    /** The user's phones, most recently registered first. */
    List<DeviceToken> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token")
    int deleteByToken(@Param("token") String token);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.user.id = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

    /**
     * Phones of everyone who wants the daily reminder and hasn't posted since {@code since}
     * (the start of the day), with their users.
     */
    @Query("""
            select d from DeviceToken d join fetch d.user u
            where u.pushDailyReminder = true and u.banned = false
              and not exists (select p.id from Post p where p.author = u and p.createdAt >= :since)""")
    List<DeviceToken> findDueDailyReminder(@Param("since") Instant since);
}
