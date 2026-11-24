package com.example.lizma.model.dto;

/** The signed-in user. {@code admin} decides whether the app shows the moderation screens. */
public record MeResponse(Long id, String username, boolean admin, String displayName, String avatarUrl) {
}
