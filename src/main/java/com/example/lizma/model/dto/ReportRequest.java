package com.example.lizma.model.dto;

import com.example.lizma.model.enums.ReportReason;
import com.example.lizma.model.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportRequest {
    @NotNull(message = "targetType is required")
    private ReportTargetType targetType;

    @NotNull(message = "targetId is required")
    private Long targetId;

    @NotNull(message = "reason is required")
    private ReportReason reason;

    @Size(max = 500, message = "Details must be at most 500 characters")
    private String details;
}
