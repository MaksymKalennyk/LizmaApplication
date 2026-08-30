package com.example.lizma.repo;

import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.MusicProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMusicAccountRepository extends JpaRepository<UserMusicAccount, Long> {
    Optional<UserMusicAccount> findByUserAndProvider(Users user, MusicProvider provider);
    boolean existsByUserAndProvider(Users user, MusicProvider provider);
}