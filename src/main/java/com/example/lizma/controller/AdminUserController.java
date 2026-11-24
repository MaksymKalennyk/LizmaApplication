package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.AdminUserView;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.enums.Role;
import com.example.lizma.service.AccountService;
import com.example.lizma.service.ProfileService;
import com.example.lizma.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Moderator actions on an account itself; acting on a report is in {@link AdminReportController}. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int SEARCH_LIMIT = 30;

    private final AccountService accountService;
    private final UserService userService;
    private final ProfileService profileService;

    @Operation(summary = "Find users by part of the name, suspended ones included; without a query: the suspended users")
    @GetMapping
    public List<AdminUserView> search(@RequestParam(defaultValue = "") String query) {
        List<Users> users = query.isBlank()
                ? userService.suspended(SEARCH_LIMIT)
                : userService.searchIncludingSuspended(query.strip(), SEARCH_LIMIT);
        return users.stream()
                .map(user -> new AdminUserView(user.getId(), user.getUsername(), user.getDisplayName(),
                        UserCard.avatarUrl(user), user.isBanned(), user.getRole() == Role.ROLE_ADMIN))
                .toList();
    }

    @Operation(summary = "Suspend a user: they can't sign in, and everything they posted and their friendships are removed")
    @PostMapping("/{username}/ban")
    public Map<String, String> ban(@PathVariable String username, @AuthenticationPrincipal Users me) {
        accountService.suspend(username, me);
        return Map.of("message", "User suspended");
    }

    @Operation(summary = "Lift a suspension (what was removed does not come back)")
    @PostMapping("/{username}/unban")
    public Map<String, String> unban(@PathVariable String username, @AuthenticationPrincipal Users me) {
        accountService.reinstate(username, me);
        return Map.of("message", "User reinstated");
    }

    @Operation(summary = "Remove a user's profile photo and display name")
    @DeleteMapping("/{username}/profile")
    public Map<String, String> removeProfile(@PathVariable String username, @AuthenticationPrincipal Users me) {
        profileService.removeProfile(userService.getByUsername(username), me, null);
        return Map.of("message", "Profile photo and name removed");
    }

    @Operation(summary = "Delete an account with all of its data, e.g. on a deletion request sent to support")
    @DeleteMapping("/{username}")
    public Map<String, String> delete(@PathVariable String username, @AuthenticationPrincipal Users me) {
        accountService.deleteByModerator(username, me);
        return Map.of("message", "Account deleted");
    }
}
