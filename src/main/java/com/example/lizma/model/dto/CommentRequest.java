package com.example.lizma.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {
    @NotBlank(message = "Comment must not be empty")
    @Size(max = 2000, message = "Comment must be at most 2000 characters")
    private String content;

    /** Optional: id of the comment being answered. Replies are kept one level deep. */
    private Long parentId;
}
