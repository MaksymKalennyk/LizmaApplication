package com.example.lizma.controller;

import com.example.lizma.model.FriendRequest;
import com.example.lizma.model.dto.FriendRequestDto;
import com.example.lizma.model.dto.mappers.FriendRequestMapper;
import com.example.lizma.model.enums.FriendRequestStatus;
import com.example.lizma.service.FriendRequestService;
import com.example.lizma.service.UserService;
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

    @Operation(summary = "Send a friend request by recipient id")
    @PostMapping("/send/{recipientId}")
    public ResponseEntity<String> sendFriendRequest(@PathVariable Long recipientId, Principal principal) {
        Long requesterId = userService.getCurrentUserId(principal);
        friendRequestService.sendFriendRequest(requesterId, recipientId);
        return ResponseEntity.ok("Request sent.");
    }

    @Operation(summary = "List received (pending) friend requests")
    @GetMapping("/received")
    public ResponseEntity<List<FriendRequestDto>> received(Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        List<FriendRequest> list = friendRequestService.getReceivedFriendRequests(userId);
        return ResponseEntity.ok(list.stream().map(friendRequestMapper::toDto).collect(Collectors.toList()));
    }

    @Operation(summary = "List sent friend requests")
    @GetMapping("/sent")
    public ResponseEntity<List<FriendRequestDto>> sent(Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        List<FriendRequest> list = friendRequestService.getSentFriendRequests(userId);
        return ResponseEntity.ok(list.stream().map(friendRequestMapper::toDto).collect(Collectors.toList()));
    }

    @Operation(summary = "Respond to a friend request")
    @PutMapping("/respond/{requestId}")
    public ResponseEntity<String> respond(@PathVariable Long requestId, @RequestParam String status, Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        FriendRequestStatus requestStatus = FriendRequestStatus.valueOf(status.toUpperCase());
        friendRequestService.respondToFriendRequest(requestId, requestStatus, userId);
        return ResponseEntity.ok("Successfully responded.");
    }

    @Operation(summary = "Generate a friend request link")
    @PostMapping("/generate-link")
    public ResponseEntity<String> generateFriendRequestLink(Principal principal) {
        Long requesterId = userService.getCurrentUserId(principal);
        String linkUrl = friendRequestService.generateFriendRequestLink(requesterId);
        System.out.println(linkUrl);
        return ResponseEntity.ok(linkUrl);
    }
}
