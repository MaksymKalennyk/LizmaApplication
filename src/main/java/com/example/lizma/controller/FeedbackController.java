package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FeedbackRequest;
import com.example.lizma.service.FeedbackService;
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
@RequestMapping("/user/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "Send an idea, a problem or another message to the team (at most 5 an hour)")
    @PostMapping
    public Map<String, String> send(@Valid @RequestBody FeedbackRequest request, @AuthenticationPrincipal Users me) {
        feedbackService.submit(me.getId(), request);
        return Map.of("message", "Thanks! We read every message.");
    }
}
