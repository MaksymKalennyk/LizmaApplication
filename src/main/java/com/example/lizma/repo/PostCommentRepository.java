package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostOrderByCreatedAtAsc(Post post);
    long countByPost(Post post);
}
