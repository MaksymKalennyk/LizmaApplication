package com.example.lizma.model.dto;

/**
 * A user's profile as the viewer sees it. Posts are for friends (and yourself) only: {@code canSeePosts}
 * says whether the posts list can be asked for, {@code postsCount} is null when it can't.
 * {@code requestId} is the pending friend request between the two, if any (to cancel or answer it).
 */
public record ProfileView(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        Relationship relationship,
        Long requestId,
        boolean canSeePosts,
        Long postsCount) {

    public enum Relationship { SELF, FRIEND, REQUEST_SENT, REQUEST_RECEIVED, NONE }
}
