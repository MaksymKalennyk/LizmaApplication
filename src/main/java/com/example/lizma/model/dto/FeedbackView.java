package com.example.lizma.model.dto;

import com.example.lizma.model.enums.FeedbackCategory;
import com.example.lizma.model.enums.FeedbackStatus;

import java.time.Instant;
import java.util.List;

/** Feedback as moderators see it. */
public record FeedbackView(
        Long id,
        FeedbackCategory category,
        String message,
        String authorUsername,
        String contactEmail,
        String appVersion,
        String osVersion,
        FeedbackStatus status,
        Instant createdAt,
        Instant handledAt,
        String handledByUsername,
        /** Screenshots, for moderators: /admin/attachments/{token}.jpg. */
        List<String> attachments) {
}
