package com.example.lizma.repo;

import com.example.lizma.model.ExtensionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExtensionKeyRepository extends JpaRepository<ExtensionKey, Long> {

    @Query("select k from ExtensionKey k join fetch k.user where k.keyHash = :hash and k.purpose = :purpose")
    Optional<ExtensionKey> findByKeyHash(@Param("hash") String keyHash, @Param("purpose") ExtensionKey.Purpose purpose);

    Optional<ExtensionKey> findByFamilyIdAndPurpose(String familyId, ExtensionKey.Purpose purpose);

    /** Of these users, the ones whose key of this purpose was used since {@code since} (e.g. a widget on a screen). */
    @Query("""
            select distinct k.user.id from ExtensionKey k
            where k.purpose = :purpose and k.user.id in :userIds and k.lastUsedAt >= :since""")
    List<Long> findUsersWithKeyUsedSince(@Param("purpose") ExtensionKey.Purpose purpose,
                                         @Param("userIds") Collection<Long> userIds, @Param("since") Instant since);

    /** Keys whose sign-in has ended: no refresh token of their family is still usable. */
    @Modifying
    @Query("""
            delete from ExtensionKey k where not exists (
              select t.id from RefreshToken t where t.familyId = k.familyId and t.revokedAt is null and t.expiresAt > :now)""")
    int deleteOrphaned(@Param("now") Instant now);
}
