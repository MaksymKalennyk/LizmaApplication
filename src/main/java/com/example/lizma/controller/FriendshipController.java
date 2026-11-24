package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FriendshipDto;
import com.example.lizma.model.dto.mappers.FriendshipMapper;
import com.example.lizma.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final FriendshipMapper friendshipMapper;

    @Operation(summary = "Get list of friends")
    @GetMapping
    public List<FriendshipDto> getFriends(@AuthenticationPrincipal Users me) {
        return friendshipService.getUserFriendships(me.getId()).stream()
                .map(friendship -> friendshipMapper.toDto(friendship, me.getId()))
                .toList();
    }

    @Operation(summary = "Remove a friend (they aren't told); either of you can send a new request later")
    @DeleteMapping("/{username}")
    public Map<String, String> removeFriend(@PathVariable String username, @AuthenticationPrincipal Users me) {
        friendshipService.unfriend(me, username);
        return Map.of("message", "Removed from friends");
    }
}
