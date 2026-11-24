package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.PostReaction;
import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {
    Optional<PostReaction> findByPostAndUser(Post post, Users user);
    java.util.List<PostReaction> findByPost(Post post);

    /** Rows of (post id, reaction type, count) for the posts; the type is null for old heart reactions. */
    @Query("select r.post.id, r.type, count(r) from PostReaction r where r.post.id in :postIds group by r.post.id, r.type")
    java.util.List<Object[]> countByPostAndType(@Param("postIds") java.util.Collection<Long> postIds);

    /** Rows of (post id, reaction type) of one user on the posts. */
    @Query("select r.post.id, r.type from PostReaction r where r.post.id in :postIds and r.user.id = :userId")
    java.util.List<Object[]> findTypesOfUser(@Param("postIds") java.util.Collection<Long> postIds, @Param("userId") Long userId);
}
