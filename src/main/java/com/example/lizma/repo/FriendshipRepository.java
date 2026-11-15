package com.example.lizma.repo;

import com.example.lizma.model.Friendship;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    List<Friendship> findByUser1AndStatusOrUser2AndStatus(Users user1, FriendshipStatus status1, Users user2, FriendshipStatus status2);

    /** True if the two users have a friendship with the given status, in either column order. */
    @Query("""
            select case when count(f) > 0 then true else false end from Friendship f
            where f.status = :status
              and ((f.user1 = :a and f.user2 = :b) or (f.user1 = :b and f.user2 = :a))
            """)
    boolean existsBetween(@Param("a") Users a, @Param("b") Users b, @Param("status") FriendshipStatus status);
}
