package com.example.lizma.model.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private Long authorId;
    private String authorUsername;
    private String content;
    private Instant createdAt;
    private Long parentId;
    private long likesCount;
    private boolean isLikedByCurrentUser;
    private java.util.List<CommentResponse> replies;
}
