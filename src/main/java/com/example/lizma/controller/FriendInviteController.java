package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.InviteLinkDto;
import com.example.lizma.repo.UserRepository;
import com.example.lizma.service.FriendshipService;
import com.example.lizma.service.UserService;
import io.swagger.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/friend-invites")
public class FriendInviteController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendshipService friendshipService;

    @Value("${app.externalBaseUrl}")
    private String externalBaseUrl;

    @Operation(summary = "Create vanity invite link by username")
    @PostMapping("/vanity-link")
    public InviteLinkDto createVanity(Principal principal) {
        Long me = userService.getCurrentUserId(principal);
        Users u = userRepository.findById(me).orElseThrow();
        return new InviteLinkDto(null, externalBaseUrl + "/" + u.getUsername(), null);
    }

    @Operation(summary = "Accept invite by requester's username (auth required)")
    @PostMapping("/accept-by-username/{username}")
    public ResponseEntity<Map<String, String>> acceptByUsername(@PathVariable String username, Principal principal) {
        Long me = userService.getCurrentUserId(principal);
        Users requester = userRepository.findByUsername(username).orElseThrow();
        friendshipService.ensureFriendship(requester.getId(), me);
        return ResponseEntity.ok(Map.of("message", "Friendship established"));
    }
}