package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.InviteLinkDto;
import com.example.lizma.service.FriendInviteService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/friend-invites")
public class FriendInviteController {

    private final FriendInviteService inviteService;

    @Operation(summary = "Create a new single-use invite link (valid for app.invites.ttlHours)")
    @PostMapping
    public InviteLinkDto createInvite(@AuthenticationPrincipal Users me) {
        return inviteService.createInvite(me.getId());
    }

    @Operation(summary = "Accept an invite link: become friends with the person who shared it")
    @PostMapping("/{token}/accept")
    public Map<String, String> acceptInvite(@PathVariable String token, @AuthenticationPrincipal Users me) {
        String message = switch (inviteService.acceptInvite(token, me.getId())) {
            case BECAME_FRIENDS -> "Friendship established";
            case ALREADY_FRIENDS -> "You are already friends";
        };
        return Map.of("message", message);
    }
}
