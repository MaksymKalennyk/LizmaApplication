package com.example.lizma.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReactionDetailResponse {
    private Long authorId;
    private String authorUsername;
    private String type;
    private Instant createdAt;
}
