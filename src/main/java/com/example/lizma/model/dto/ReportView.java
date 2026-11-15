package com.example.lizma.model.dto;

import com.example.lizma.model.enums.ReportReason;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;

import java.time.Instant;

/** A report as moderators see it. {@code targetPreview} is null when the content is already gone. */
public record ReportView(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        String targetUsername,
        String targetPreview,
        ReportReason reason,
        String details,
        String reporterUsername,
        ReportStatus status,
        Instant createdAt,
        Instant resolvedAt) {
}
