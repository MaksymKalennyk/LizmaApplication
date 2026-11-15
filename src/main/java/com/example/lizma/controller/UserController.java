package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Find a user by exact username")
    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String username) {
        Users user = userService.getByUsername(username.strip());
        return Map.of("id", user.getId(), "username", user.getUsername());
    }

    @Operation(summary = "Search other users by part of the username")
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("query") String query,
                                            @RequestParam(value = "limit", defaultValue = "20") int limit,
                                            @AuthenticationPrincipal Users me) {
        if (query.isBlank()) {
            return List.of();
        }
        return userService.searchByUsername(query.strip(), limit).stream()
                .filter(user -> !user.getId().equals(me.getId()))
                .map(user -> Map.<String, Object>of("id", user.getId(), "username", user.getUsername()))
                .toList();
    }
}
