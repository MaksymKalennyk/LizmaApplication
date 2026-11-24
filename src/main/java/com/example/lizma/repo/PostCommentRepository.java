package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostOrderByCreatedAtAsc(Post post);
    long countByPost(Post post);

    /** Rows of (post id, number of comments) for the posts that have any. */
    @Query("select c.post.id, count(c) from PostComment c where c.post.id in :postIds group by c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") java.util.Collection<Long> postIds);
}
