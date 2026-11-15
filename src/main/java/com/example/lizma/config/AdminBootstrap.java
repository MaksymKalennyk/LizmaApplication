package com.example.lizma.config;

import com.example.lizma.model.enums.Role;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives the moderator role to the users listed in {@code app.admin-usernames} (comma-separated) at startup.
 * Moderators handle reports through /admin/reports (e.g. from Swagger UI).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${app.admin-usernames:}")
    private List<String> adminUsernames;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : adminUsernames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            userRepository.findByUsername(name.strip()).ifPresentOrElse(user -> {
                if (user.getRole() != Role.ROLE_ADMIN) {
                    user.setRole(Role.ROLE_ADMIN);
                    userRepository.save(user);
                    log.info("Gave moderator role to {}", user.getUsername());
                }
            }, () -> log.warn("app.admin-usernames: user {} doesn't exist (yet)", name.strip()));
        }
    }
}
