package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookup(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        Users user;
        try {
            user = userService.getByUsername(username);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    @GetMapping("/search")
    public ResponseEntity<java.util.List<Map<String, Object>>> search(@RequestParam("query") String query,
                                                                      @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(java.util.List.of());
        }
        var users = userService.searchByUsername(query, limit == null ? 20 : limit);
        var list = new java.util.ArrayList<Map<String, Object>>(users.size());
        for (var u : users) {
            list.add(java.util.Map.of("id", u.getId(), "username", u.getUsername()));
        }
        return ResponseEntity.ok(list);
    }

}
