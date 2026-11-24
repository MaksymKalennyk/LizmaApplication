package com.example.lizma.controller;

import com.example.lizma.model.dto.AdminSummary;
import com.example.lizma.model.dto.ModerationLogEntry;
import com.example.lizma.service.FeedbackService;
import com.example.lizma.service.ModerationLogService;
import com.example.lizma.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What waits for moderators, and what they did. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final ReportService reportService;
    private final FeedbackService feedbackService;
    private final ModerationLogService moderationLog;

    @Operation(summary = "Open reports and new feedback, for the badge in the app")
    @GetMapping("/summary")
    public AdminSummary summary() {
        return new AdminSummary(reportService.countOpen(), feedbackService.countNew());
    }

    @Operation(summary = "Moderators' recent actions, newest first")
    @GetMapping("/log")
    public List<ModerationLogEntry> log(@RequestParam(defaultValue = "100") int limit) {
        return moderationLog.recent(limit);
    }
}
