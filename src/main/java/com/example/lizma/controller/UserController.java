package com.example.lizma.controller;

import com.example.lizma.exception.UserNotFoundException;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.DeleteAccountRequest;
import com.example.lizma.model.dto.MeResponse;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.enums.Role;
import com.example.lizma.service.AccountService;
import com.example.lizma.service.BlockService;
import com.example.lizma.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BlockService blockService;
    private final AccountService accountService;

    @Operation(summary = "The signed-in user; `admin` decides whether the app shows the moderation screens")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Users me) {
        return new MeResponse(me.getId(), me.getUsername(), me.getRole() == Role.ROLE_ADMIN,
                me.getDisplayName(), UserCard.avatarUrl(me));
    }

    @Operation(summary = "Find a user by exact username (blocked and suspended users are not found)")
    @GetMapping("/lookup")
    public UserCard lookup(@RequestParam String username, @AuthenticationPrincipal Users me) {
        Users user = userService.getByUsername(username.strip());
        if (user.isBanned() || blockService.blockedEitherWay(me.getId(), user.getId())) {
            throw new UserNotFoundException("User not found");
        }
        return summary(user);
    }

    @Operation(summary = "Search other users by part of the username (blocked and suspended users are left out)")
    @GetMapping("/search")
    public List<UserCard> search(@RequestParam("query") String query,
                                            @RequestParam(value = "limit", defaultValue = "20") int limit,
                                            @AuthenticationPrincipal Users me) {
        if (query.isBlank()) {
            return List.of();
        }
        Set<Long> hidden = blockService.hiddenUserIds(me.getId());
        return userService.searchByUsername(query.strip(), limit).stream()
                .filter(user -> !user.getId().equals(me.getId()) && !hidden.contains(user.getId()))
                .map(UserController::summary)
                .toList();
    }

    @Operation(summary = "Users you blocked")
    @GetMapping("/blocks")
    public List<UserCard> blocked(@AuthenticationPrincipal Users me) {
        return blockService.blockedBy(me.getId()).stream().map(UserController::summary).toList();
    }

    @Operation(summary = "Block a user: removes the friendship and pending requests, hides each other's content")
    @PostMapping("/blocks/{userId}")
    public Map<String, String> block(@PathVariable Long userId, @AuthenticationPrincipal Users me) {
        blockService.block(me.getId(), userId);
        return Map.of("message", "User blocked");
    }

    @Operation(summary = "Unblock a user")
    @DeleteMapping("/blocks/{userId}")
    public Map<String, String> unblock(@PathVariable Long userId, @AuthenticationPrincipal Users me) {
        blockService.unblock(me.getId(), userId);
        return Map.of("message", "User unblocked");
    }

    @Operation(summary = "Permanently delete your account and all of its data (needs your password)")
    @PostMapping("/me/delete")
    public Map<String, String> deleteAccount(@Valid @RequestBody DeleteAccountRequest request, @AuthenticationPrincipal Users me) {
        accountService.deleteAccount(me.getId(), request.password());
        return Map.of("message", "Account deleted");
    }

    private static UserCard summary(Users user) {
        return UserCard.of(user);
    }
}
