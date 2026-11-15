package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FriendRequestDto;
import com.example.lizma.model.dto.mappers.FriendRequestMapper;
import com.example.lizma.service.FriendRequestService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;
    private final FriendRequestMapper friendRequestMapper;

    @Operation(summary = "Send a friend request by recipient id (accepts theirs if they already asked you)")
    @PostMapping("/send/{recipientId}")
    public String sendFriendRequest(@PathVariable Long recipientId, @AuthenticationPrincipal Users me) {
        return switch (friendRequestService.sendFriendRequest(me.getId(), recipientId)) {
            case SENT -> "Request sent.";
            case BECAME_FRIENDS -> "You are now friends!";
        };
    }

    @Operation(summary = "List received (pending) friend requests")
    @GetMapping("/received")
    public List<FriendRequestDto> received(@AuthenticationPrincipal Users me) {
        return friendRequestService.getReceivedFriendRequests(me.getId()).stream()
                .map(friendRequestMapper::toDto)
                .toList();
    }

    @Operation(summary = "List sent friend requests that are still pending")
    @GetMapping("/sent")
    public List<FriendRequestDto> sent(@AuthenticationPrincipal Users me) {
        return friendRequestService.getSentFriendRequests(me.getId()).stream()
                .map(friendRequestMapper::toDto)
                .toList();
    }

    @Operation(summary = "Respond to a friend request: status=ACCEPTED or DECLINED")
    @PutMapping("/respond/{requestId}")
    public String respond(@PathVariable Long requestId, @RequestParam String status, @AuthenticationPrincipal Users me) {
        friendRequestService.respondToFriendRequest(requestId, status, me.getId());
        return "Successfully responded.";
    }
}
