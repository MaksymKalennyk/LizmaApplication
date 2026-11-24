package com.example.lizma.service;

import com.example.lizma.exception.ConflictException;
import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.Users;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserRepository userRepository;

    public Users create(Users user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ConflictException("Username already taken");
        }
        return userRepository.save(user);
    }

    public Users getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public Users getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    /** For Spring Security: must throw {@link UsernameNotFoundException} for unknown users. */
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /** Users whose name contains the query; suspended accounts are not listed. */
    public List<Users> searchByUsername(String query, int limit) {
        int size = (limit <= 0 || limit > MAX_SEARCH_LIMIT) ? DEFAULT_SEARCH_LIMIT : limit;
        return userRepository.findByUsernameContainingIgnoreCaseAndBannedFalse(query, PageRequest.of(0, size));
    }

    /** For moderators: users whose name contains the query, suspended ones included. */
    public List<Users> searchIncludingSuspended(String query, int limit) {
        return userRepository.findByUsernameContainingIgnoreCaseOrderByUsernameAsc(query, PageRequest.of(0, limit));
    }

    /** For moderators: suspended accounts, by name. */
    public List<Users> suspended(int limit) {
        return userRepository.findByBannedTrueOrderByUsernameAsc(PageRequest.of(0, limit));
    }

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }
}
