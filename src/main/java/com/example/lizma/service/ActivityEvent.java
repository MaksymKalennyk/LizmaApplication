package com.example.lizma.service;

/**
 * Something one user did that another may get a push notification about. Published inside the
 * transaction that did it; {@link com.example.lizma.service.push.PushNotifier} reacts once it's committed.
 */
public sealed interface ActivityEvent {

    /** {@code requesterId} asked {@code recipientId} to be friends. */
    record FriendRequestSent(Long requesterId, Long recipientId) implements ActivityEvent {
    }

    /** {@code actorId} made them friends (accepted a request or an invite); {@code recipientId} hears about it. */
    record BecameFriends(Long actorId, Long recipientId) implements ActivityEvent {
    }

    /** {@code reactorId} reacted to a post for the first time (changing the emoji later isn't news). */
    record PostReacted(Long postId, Long reactorId, String reaction) implements ActivityEvent {
    }

    /** A comment or a reply was added. */
    record CommentAdded(Long commentId) implements ActivityEvent {
    }
}
