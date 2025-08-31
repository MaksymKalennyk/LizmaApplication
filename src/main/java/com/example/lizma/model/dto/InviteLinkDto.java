package com.example.lizma.model.dto;

import io.swagger.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
@Schema(description = "Friend invite link")
public class InviteLinkDto {
    private String token;
    private String url;
    private String expiresAt;
}
