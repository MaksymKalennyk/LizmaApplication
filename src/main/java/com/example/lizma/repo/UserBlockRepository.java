package com.example.lizma.repo;

import com.example.lizma.model.UserBlock;
import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerAndBlocked(Users blocker, Users blocked);

    List<UserBlock> findByBlockerOrderByCreatedAtDesc(Users blocker);

    @Modifying
    @Query("delete from UserBlock b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    void deleteByIds(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** True if either user blocked the other. */
    @Query("""
            select case when count(b) > 0 then true else false end from UserBlock b
            where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    @Query("select b.blocked.id from UserBlock b where b.blocker.id = :userId")
    List<Long> findBlockedIds(@Param("userId") Long userId);

    @Query("select b.blocker.id from UserBlock b where b.blocked.id = :userId")
    List<Long> findBlockerIds(@Param("userId") Long userId);
}
