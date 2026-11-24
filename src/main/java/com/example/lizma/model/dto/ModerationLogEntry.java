package com.example.lizma.model.dto;

import com.example.lizma.model.enums.ModerationActionType;

import java.time.Instant;

/** One line of the moderation log. {@code moderatorUsername} is null if that account no longer exists. */
public record ModerationLogEntry(
        Long id,
        ModerationActionType action,
        String moderatorUsername,
        String targetUsername,
        Long reportId,
        Instant createdAt) {
}
