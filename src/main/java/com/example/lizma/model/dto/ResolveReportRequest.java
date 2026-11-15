package com.example.lizma.model.dto;

import com.example.lizma.model.enums.ReportAction;
import jakarta.validation.constraints.NotNull;

/**
 * REMOVE_CONTENT deletes the post/comment; BAN_USER suspends its author (or the reported user) and removes
 * everything they posted; DISMISS closes the report.
 */
public record ResolveReportRequest(@NotNull(message = "action is required") ReportAction action) {
}
