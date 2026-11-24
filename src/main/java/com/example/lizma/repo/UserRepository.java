package com.example.lizma.repo;

import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.example.lizma.model.enums.Role;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Suspended accounts are left out: nobody should be able to find or add them. */
    List<Users> findByUsernameContainingIgnoreCaseAndBannedFalse(String query, Pageable pageable);

    /** For moderators: suspended accounts included. */
    List<Users> findByUsernameContainingIgnoreCaseOrderByUsernameAsc(String query, Pageable pageable);

    List<Users> findByBannedTrueOrderByUsernameAsc(Pageable pageable);

    List<Users> findByRole(Role role);
}
