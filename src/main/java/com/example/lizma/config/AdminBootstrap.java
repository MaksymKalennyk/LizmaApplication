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

import com.example.lizma.model.Users;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes exactly the users listed in {@code app.admin-usernames} (ADMIN_USERNAMES in .env, comma-separated)
 * moderators at startup: listed users get the role, everyone else loses it. Nobody becomes a moderator any
 * other way. Moderators handle reports and feedback in the app (Profile → Moderation) or through /admin/**.
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
        Set<String> listed = adminUsernames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .collect(Collectors.toSet());

        for (Users admin : userRepository.findByRole(Role.ROLE_ADMIN)) {
            if (!listed.contains(admin.getUsername())) {
                admin.setRole(Role.ROLE_USER);
                log.warn("Took the moderator role from {}: not in app.admin-usernames", admin.getUsername());
            }
        }
        for (String name : listed) {
            userRepository.findByUsername(name).ifPresentOrElse(user -> {
                if (user.getRole() != Role.ROLE_ADMIN) {
                    user.setRole(Role.ROLE_ADMIN);
                    log.info("Gave moderator role to {}", user.getUsername());
                }
            }, () -> log.warn("app.admin-usernames: user {} doesn't exist (yet)", name));
        }
    }
}
