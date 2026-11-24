package com.example.lizma.repo;

import com.example.lizma.model.WidgetKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WidgetKeyRepository extends JpaRepository<WidgetKey, Long> {

    @Query("select k from WidgetKey k join fetch k.user where k.keyHash = :hash")
    Optional<WidgetKey> findByKeyHash(@Param("hash") String keyHash);

    Optional<WidgetKey> findByFamilyId(String familyId);

    /** Of these users, the ones whose widget fetched since {@code since}: their widget is on a screen. */
    @Query("select distinct k.user.id from WidgetKey k where k.user.id in :userIds and k.lastUsedAt >= :since")
    List<Long> findUsersWithWidgetSince(@Param("userIds") Collection<Long> userIds, @Param("since") Instant since);

    /** Keys whose sign-in has ended: no refresh token of their family is still usable. */
    @Modifying
    @Query("""
            delete from WidgetKey k where not exists (
              select t.id from RefreshToken t where t.familyId = k.familyId and t.revokedAt is null and t.expiresAt > :now)""")
    int deleteOrphaned(@Param("now") Instant now);
}
