package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ReportRequest;
import com.example.lizma.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Report a post, a comment or a user to the moderators")
    @PostMapping
    public Map<String, String> report(@Valid @RequestBody ReportRequest request, @AuthenticationPrincipal Users me) {
        reportService.report(me.getId(), request);
        return Map.of("message", "Thanks for the report. We'll review it within 24 hours.");
    }
}
