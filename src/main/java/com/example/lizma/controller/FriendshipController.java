package com.example.lizma.controller;

import com.example.lizma.model.dto.FriendshipDto;
import com.example.lizma.model.dto.mappers.FriendshipMapper;
import com.example.lizma.service.FriendshipService;
import com.example.lizma.service.UserService;
import io.swagger.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserService userService;
    private final FriendshipMapper friendshipMapper;

    @Operation(summary = "Get list of friends")
    @GetMapping
    public ResponseEntity<List<FriendshipDto>> getFriends(Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        List<FriendshipDto> friends = friendshipService.getUserFriendships(userId).stream()
                .map(friendship -> friendshipMapper.toDto(friendship, userId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(friends);
    }
}