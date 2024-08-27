package com.example.lizma.service;

import com.example.lizma.model.Role;
import com.example.lizma.model.Users;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public Users save(Users users){
        return userRepository.save(users);
    }

    public Users create(Users user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("A user with this username already exists");
        }

        return save(user);
    }

    public Users getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserDetailsService userDetailsService() {
        return this::getByUsername;
    }

    public Users getCurrentUser() {
        var username = SecurityContextHolder.getContext().getAuthentication().getName();
        return getByUsername(username);
    }

    public Long getUserIdFromToken(String token) {
        return jwtService.extractUserId(token);
    }

    public void getAdmin() {
        var user = getCurrentUser();
        user.setRole(Role.ROLE_ADMIN);
        save(user);
    }
}
