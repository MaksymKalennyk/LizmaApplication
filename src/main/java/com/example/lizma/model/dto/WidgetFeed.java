package com.example.lizma.model.dto;

import java.time.Instant;
import java.util.List;

/** What the home screen widget shows: friends' latest songs, newest first. */
public record WidgetFeed(List<Post> posts) {

    /**
     * @param authorName      the display name, or the username without one
     * @param authorAvatarUrl relative to the server ({@code /avatars/...}), null without a photo
     */
    public record Post(Long id, String authorUsername, String authorName, String authorAvatarUrl,
                       String trackName, String artistName, String albumCoverUrl, Instant createdAt) {
    }
}
