package com.example.lizma.repo;

import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUsername(String username);
    boolean existsByUsername(String username);

    List<Users> findByUsernameContainingIgnoreCase(String query, Pageable pageable);
    List<Users> findTop20ByUsernameContainingIgnoreCase(String query);
}
