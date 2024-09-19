package com.example.lizma.controller;

import com.example.lizma.service.FriendRequestService;
import com.example.lizma.service.UserService;
import io.swagger.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/user/friend-request")
@RequiredArgsConstructor
public class FriendRequestLinkController {

    private final FriendRequestService friendRequestService;
    private final UserService userService;

    @Operation(summary = "Process the friend request link")
    @GetMapping("/accept")
    public ResponseEntity<String> acceptFriendRequest(@RequestParam("token") String token, Principal principal) {
        Long recipientId = userService.getCurrentUserId(principal);
        String result = friendRequestService.processFriendRequestLink(token, recipientId);
        return ResponseEntity.ok(result);
    }
}
