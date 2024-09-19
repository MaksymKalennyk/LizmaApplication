package com.example.lizma.model.dto;

import lombok.Data;

@Data
public class FriendRequestDto {
    private Long id;
    private Long requesterId;
    private String requesterUsername;
    private Long recipientId;
    private String recipientUsername;
    private String status;
}
