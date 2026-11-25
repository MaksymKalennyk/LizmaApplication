package com.example.lizma.service;

import com.example.lizma.model.ExtensionKey;
import com.example.lizma.model.Post;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.model.dto.WidgetFeed;
import com.example.lizma.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The home screen widget: friends' latest songs, read with a key of the widget's own (see ExtensionKeyService). The
 * key belongs to the sign-in on that phone and dies with it, so signing out (or a suspension) also empties the widget.
 */
@Service
@RequiredArgsConstructor
public class WidgetService {

    /** As many as the largest widget shows. */
    static final int FEED_SIZE = 4;

    private final ExtensionKeyService extensionKeys;
    private final FriendshipService friendshipService;
    private final PostRepository postRepository;

    /**
     * A key for the widget of the phone signed in with this refresh token. Asking again replaces that sign-in's
     * previous key.
     */
    public String issue(Users me, String refreshToken) {
        return extensionKeys.issue(me, refreshToken, ExtensionKey.Purpose.WIDGET, "Sign in again to set up the widget");
    }

    /** Friends' latest songs, newest first; empty when the key is unknown or its sign-in has ended. */
    @Transactional
    public Optional<WidgetFeed> feed(String rawKey) {
        return extensionKeys.authenticate(rawKey, ExtensionKey.Purpose.WIDGET).map(this::feedOf);
    }

    /** Of these users, those whose widget has fetched lately, i.e. it's on their screen. */
    public List<Long> usersWithWidget(Collection<Long> userIds, Duration within) {
        return extensionKeys.usersWithKeyUsed(ExtensionKey.Purpose.WIDGET, userIds, within);
    }

    private WidgetFeed feedOf(Users user) {
        List<Long> friendIds = friendshipService.getFriendIds(user);
        if (friendIds.isEmpty()) {
            return new WidgetFeed(List.of());
        }
        List<WidgetFeed.Post> posts = postRepository.findFeedPage(friendIds, PageRequest.of(0, FEED_SIZE)).stream()
                .map(WidgetService::toWidgetPost)
                .toList();
        return new WidgetFeed(posts);
    }

    private static WidgetFeed.Post toWidgetPost(Post post) {
        Users author = post.getAuthor();
        String name = author.getDisplayName() != null && !author.getDisplayName().isBlank()
                ? author.getDisplayName() : author.getUsername();
        return new WidgetFeed.Post(post.getId(), author.getUsername(), name, UserCard.avatarUrl(author),
                post.getTrackName(), post.getArtistName(), post.getAlbumCoverUrl(), post.getCreatedAt());
    }
}
