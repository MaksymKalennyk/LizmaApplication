package com.example.lizma.model.dto;

import com.example.lizma.model.Users;

/**
 * How a user is shown anywhere in the app: the username (unique handle), the display name if they set one
 * and the photo. {@code avatarUrl} is a path on this server (/avatars/…), null without a photo.
 */
public record UserCard(Long id, String username, String displayName, String avatarUrl) {

    public static UserCard of(Users user) {
        return new UserCard(user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl(user));
    }

    public static String avatarUrl(Users user) {
        return user.getAvatarToken() == null ? null : "/avatars/" + user.getAvatarToken() + ".jpg";
    }
}
