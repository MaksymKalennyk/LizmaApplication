package com.example.lizma.controller;

import com.example.lizma.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Moderator actions on an account itself; acting on a report is in {@link AdminReportController}. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AccountService accountService;

    @Operation(summary = "Suspend a user: they can't sign in, and everything they posted and their friendships are removed")
    @PostMapping("/{username}/ban")
    public Map<String, String> ban(@PathVariable String username) {
        accountService.suspend(username);
        return Map.of("message", "User suspended");
    }

    @Operation(summary = "Lift a suspension (what was removed does not come back)")
    @PostMapping("/{username}/unban")
    public Map<String, String> unban(@PathVariable String username) {
        accountService.reinstate(username);
        return Map.of("message", "User reinstated");
    }

    @Operation(summary = "Delete an account with all of its data, e.g. on a deletion request sent to support")
    @DeleteMapping("/{username}")
    public Map<String, String> delete(@PathVariable String username) {
        accountService.deleteByModerator(username);
        return Map.of("message", "Account deleted");
    }
}
