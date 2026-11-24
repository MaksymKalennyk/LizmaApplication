package com.example.lizma.model.dto;

/** A user as moderators see them, suspended ones included. */
public record AdminUserView(Long id, String username, String displayName, String avatarUrl, boolean banned, boolean admin) {
}
