package com.example.lizma.controller;

import com.example.lizma.model.dto.mappers.FriendRequestMapper;
import com.example.lizma.service.FriendRequestService;
import com.example.lizma.service.UserService;
import com.example.lizma.model.dto.FriendRequestDto;
import com.example.lizma.model.enums.FriendRequestStatus;
import io.swagger.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;
    private final UserService userService;
    private final FriendRequestMapper friendRequestMapper;

    @Operation(summary = "Send a friend request")
    @PostMapping("/send/{recipientId}")
    public ResponseEntity<String> sendFriendRequest(@PathVariable Long recipientId, Principal principal) {
        Long requesterId = userService.getCurrentUserId(principal);
        friendRequestService.sendFriendRequest(requesterId, recipientId);
        return ResponseEntity.ok("Запит на дружбу відправлено.");
    }

    @Operation(summary = "Get received friend requests")
    @GetMapping("/received")
    public ResponseEntity<List<FriendRequestDto>> getReceivedFriendRequests(Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        List<FriendRequestDto> requests = friendRequestService.getReceivedRequests(userId).stream()
                .map(friendRequestMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(requests);
    }

    @Operation(summary = "Respond to a friend request")
    @PutMapping("/respond/{requestId}")
    public ResponseEntity<String> respondToFriendRequest(
            @PathVariable Long requestId,
            @RequestParam String status,
            Principal principal) {

        Long userId = userService.getCurrentUserId(principal);
        FriendRequestStatus requestStatus = FriendRequestStatus.valueOf(status.toUpperCase());
        friendRequestService.respondToFriendRequest(requestId, requestStatus, userId);
        return ResponseEntity.ok("Статус запиту оновлено.");
    }

    @Operation(summary = "Generate a friend request link")
    @PostMapping("/generate-link")
    public ResponseEntity<String> generateFriendRequestLink(Principal principal) {
        Long requesterId = userService.getCurrentUserId(principal);
        String linkUrl = friendRequestService.generateFriendRequestLink(requesterId);
        return ResponseEntity.ok(linkUrl);
    }
}
