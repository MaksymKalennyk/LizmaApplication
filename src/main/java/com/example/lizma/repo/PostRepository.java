package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.Users;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByAuthorInOrderByCreatedAtDesc(List<Users> authors, Pageable pageable);
}
