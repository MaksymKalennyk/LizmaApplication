package com.example.lizma.model.dto;

import lombok.Data;

@Data
public class FriendRequestDto {
    private Long id;
    private Long requesterId;
    private String requesterUsername;
    private String requesterDisplayName;
    private String requesterAvatarUrl;
    private Long recipientId;
    private String recipientUsername;
    private String recipientDisplayName;
    private String recipientAvatarUrl;
    private String status;
}
