package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FeedbackView;
import com.example.lizma.model.enums.FeedbackStatus;
import com.example.lizma.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Feedback inbox for moderators. */
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "Feedback by status (NEW by default), newest first")
    @GetMapping
    public List<FeedbackView> list(@RequestParam(defaultValue = "NEW") FeedbackStatus status) {
        return feedbackService.list(status);
    }

    @Operation(summary = "Mark feedback as done")
    @PostMapping("/{id}/done")
    public Map<String, String> done(@PathVariable Long id, @AuthenticationPrincipal Users me) {
        feedbackService.markDone(id, me);
        return Map.of("message", "Marked as done");
    }

    @Operation(summary = "Move feedback back to NEW")
    @PostMapping("/{id}/reopen")
    public Map<String, String> reopen(@PathVariable Long id) {
        feedbackService.reopen(id);
        return Map.of("message", "Moved back to new");
    }
}
