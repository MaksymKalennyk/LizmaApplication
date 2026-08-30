package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.PostReaction;
import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {
    long countByPost(Post post);
    boolean existsByPostAndUser(Post post, Users user);
    Optional<PostReaction> findByPostAndUser(Post post, Users user);
    java.util.List<PostReaction> findByPost(Post post);
}
