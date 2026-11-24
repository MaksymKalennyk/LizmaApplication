package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ReportView;
import com.example.lizma.model.dto.ResolveReportRequest;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Moderation queue; only for users with the admin role (see app.admin-usernames). */
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "Reports by status: OPEN (default) oldest first; RESOLVED and/or DISMISSED most recently handled first")
    @GetMapping
    public List<ReportView> list(@RequestParam(defaultValue = "OPEN") List<ReportStatus> status) {
        return reportService.list(status);
    }

    @Operation(summary = "Handle a report: REMOVE_CONTENT, BAN_USER (suspends the author and removes everything they posted) or DISMISS")
    @PostMapping("/{reportId}/resolve")
    public Map<String, String> resolve(@PathVariable Long reportId, @Valid @RequestBody ResolveReportRequest request,
                                       @AuthenticationPrincipal Users me) {
        reportService.resolve(reportId, request.action(), me);
        return Map.of("message", "Report handled");
    }
}
