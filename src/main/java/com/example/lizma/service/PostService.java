package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;
import com.example.lizma.exception.NotFoundException;
import com.example.lizma.exception.UnauthorizedActionException;
import com.example.lizma.model.Post;
import com.example.lizma.model.PostComment;
import com.example.lizma.model.PostReaction;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.CommentRequest;
import com.example.lizma.model.dto.CommentResponse;
import com.example.lizma.model.dto.PostRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.model.dto.ReactionDetailResponse;
import com.example.lizma.model.dto.UserCard;
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostReactionRepository;
import com.example.lizma.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import com.example.lizma.model.enums.MusicProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;
    /** Length of the post_reactions.reaction_type column. */
    private static final int MAX_REACTION_LENGTH = 16;
    private static final String DEFAULT_REACTION = "❤️";

    private final PostRepository postRepository;
    private final PostReactionRepository reactionRepository;
    private final PostCommentRepository commentRepository;
    private final UserService userService;
    private final FriendshipService friendshipService;
    private final BlockService blockService;
    private final ContentFilter contentFilter;
    private final ContentCleanupService cleanupService;
    private final ApplicationEventPublisher events;

    public PostResponse createPost(Long authorId, PostRequest request) {
        contentFilter.check(request.getCaption());
        Users author = userService.getById(authorId);
        Post post = Post.builder()
                .author(author)
                .musicId(request.getMusicId().strip())
                .musicProvider(request.getMusicProvider())
                .trackName(request.getTrackName().strip())
                .artistName(request.getArtistName().strip())
                .albumCoverUrl(TrustedMediaUrls.cover(request.getAlbumCoverUrl()))
                .previewUrl(TrustedMediaUrls.preview(request.getPreviewUrl()))
                .appleMusicUrl(TrustedMediaUrls.appleMusic(request.getAppleMusicUrl()))
                .caption(blankToNull(request.getCaption()))
                .build();
        Post saved = postRepository.save(post);
        // The same track in the other services is looked up once the post is saved (MusicLinkResolver).
        events.publishEvent(new PostCreatedEvent(saved.getId(), author.getId()));
        return toResponses(List.of(saved), author.getId()).get(0);
    }

    /**
     * Posts of the user's friends and the user's own posts, newest first. With {@code beforeId} (the id of
     * the last post the app has) the page continues after that post, so posts published meanwhile don't
     * shift the pages; otherwise {@code page} counts pages from the newest post.
     * <p>
     * A fixed handful of queries whatever the page size: friends, posts with their authors, reaction
     * counts, the viewer's reactions and comment counts.
     */
    @Transactional(readOnly = true)
    public List<PostResponse> getFeed(Long userId, int page, int size, Long beforeId) {
        Users viewer = userService.getById(userId);
        Set<Long> authorIds = new HashSet<>(friendshipService.getFriendIds(viewer));
        authorIds.add(viewer.getId());

        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<Post> posts = beforeId != null
                ? postRepository.findFeedPageBefore(authorIds, beforeId, PageRequest.of(0, pageSize))
                : postRepository.findFeedPage(authorIds, PageRequest.of(Math.max(page, 0), pageSize));
        return toResponses(posts, viewer.getId());
    }

    /** Same emoji again removes the reaction, a different one replaces it. */
    public void reactToPost(Long userId, Long postId, String type) {
        Users user = userService.getById(userId);
        Post post = requireVisiblePost(postId, user);
        if (post.getAuthor().getId().equals(user.getId())) {
            throw new InvalidActionException("You can't react to your own post");
        }
        String reaction = normalizeReaction(type);

        reactionRepository.findByPostAndUser(post, user).ifPresentOrElse(
                existing -> {
                    if (reaction.equals(reactionType(existing))) {
                        reactionRepository.delete(existing);
                    } else {
                        existing.setType(reaction);
                    }
                },
                () -> {
                    reactionRepository.save(PostReaction.builder().post(post).user(user).type(reaction).build());
                    events.publishEvent(new ActivityEvent.PostReacted(post.getId(), user.getId(), reaction));
                }
        );
    }

    public CommentResponse addComment(Long userId, Long postId, CommentRequest request) {
        contentFilter.check(request.getContent());
        Users author = userService.getById(userId);
        Post post = requireVisiblePost(postId, author);

        PostComment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .filter(comment -> comment.getPost().getId().equals(post.getId()))
                    .orElseThrow(() -> new NotFoundException("Parent comment not found"));
            // Replies are one level deep: answering a reply puts the answer under the same top-level comment.
            parent = topLevel(parent);
        }

        PostComment saved = commentRepository.save(PostComment.builder()
                .post(post)
                .author(author)
                .content(request.getContent().strip())
                .parentComment(parent)
                .build());
        events.publishEvent(new ActivityEvent.CommentAdded(saved.getId()));
        return toCommentResponse(saved, author.getId(), List.of());
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId, Long userId) {
        Users viewer = userService.getById(userId);
        Post post = requireVisiblePost(postId, viewer);
        Set<Long> hidden = blockService.hiddenUserIds(viewer.getId());
        List<PostComment> comments = commentRepository.findByPostOrderByCreatedAtAsc(post).stream()
                .filter(comment -> !hidden.contains(comment.getAuthor().getId()))
                .toList();

        Map<Long, List<PostComment>> repliesByThread = comments.stream()
                .filter(comment -> comment.getParentComment() != null)
                .collect(Collectors.groupingBy(comment -> topLevel(comment).getId()));

        return comments.stream()
                .filter(comment -> comment.getParentComment() == null)
                .map(comment -> toCommentResponse(comment, viewer.getId(),
                        repliesByThread.getOrDefault(comment.getId(), List.of()).stream()
                                .map(reply -> toCommentResponse(reply, viewer.getId(), List.of()))
                                .toList()))
                .toList();
    }

    public void toggleLikeComment(Long userId, Long postId, Long commentId) {
        Users user = userService.getById(userId);
        PostComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getPost().getId().equals(postId))
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        requireVisiblePost(postId, user);

        boolean unliked = comment.getLikedByUsers().removeIf(u -> u.getId().equals(user.getId()));
        if (!unliked) {
            comment.getLikedByUsers().add(user);
        }
    }

    @Transactional(readOnly = true)
    public List<ReactionDetailResponse> getReactions(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (!post.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedActionException("Only the author can see who reacted");
        }
        Set<Long> hidden = blockService.hiddenUserIds(userId);
        return reactionRepository.findByPost(post).stream()
                .filter(reaction -> !hidden.contains(reaction.getUser().getId()))
                .map(reaction -> ReactionDetailResponse.builder()
                        .authorId(reaction.getUser().getId())
                        .authorUsername(reaction.getUser().getUsername())
                        .authorDisplayName(reaction.getUser().getDisplayName())
                        .authorAvatarUrl(UserCard.avatarUrl(reaction.getUser()))
                        .type(reactionType(reaction))
                        .createdAt(reaction.getCreatedAt())
                        .build())
                .toList();
    }

    /** One post, if the viewer may see it (their own or a friend's). */
    @Transactional(readOnly = true)
    public PostResponse getPost(Long userId, Long postId) {
        Users viewer = userService.getById(userId);
        return toResponses(List.of(requireVisiblePost(postId, viewer)), viewer.getId()).get(0);
    }

    /** A user's posts, newest first, continuing after {@code beforeId} if given. Visibility is checked by the caller. */
    @Transactional(readOnly = true)
    public List<PostResponse> getPostsOf(Users author, Long viewerId, Long beforeId, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Set<Long> authorIds = Set.of(author.getId());
        List<Post> posts = beforeId != null
                ? postRepository.findFeedPageBefore(authorIds, beforeId, PageRequest.of(0, pageSize))
                : postRepository.findFeedPage(authorIds, PageRequest.of(0, pageSize));
        return toResponses(posts, viewerId);
    }

    /** The author deletes their post with its comments and reactions. */
    public void deletePost(Long userId, Long postId) {
        Users user = userService.getById(userId);
        Post post = requireVisiblePost(postId, user);
        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("You can only delete your own posts");
        }
        cleanupService.deletePost(post.getId());
    }

    /**
     * Deletes a comment (a top-level one with its replies). Allowed for the comment's author and for the
     * author of the post it is under.
     */
    public void deleteComment(Long userId, Long postId, Long commentId) {
        Users user = userService.getById(userId);
        Post post = requireVisiblePost(postId, user);
        PostComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getPost().getId().equals(post.getId()))
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        boolean allowed = comment.getAuthor().getId().equals(user.getId())
                || post.getAuthor().getId().equals(user.getId());
        if (!allowed) {
            throw new UnauthorizedActionException("You can only delete your own comments or comments on your posts");
        }
        cleanupService.deleteComment(comment.getId());
    }

    /** A post is visible to its author and the author's friends; for everyone else it doesn't exist (404). */
    @Transactional(readOnly = true)
    public Post requireVisiblePost(Long postId, Users viewer) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        boolean visible = post.getAuthor().getId().equals(viewer.getId())
                || friendshipService.areFriends(post.getAuthor(), viewer);
        if (!visible) {
            throw new NotFoundException("Post not found");
        }
        return post;
    }

    /** Responses for the posts, in their order, with counts loaded for all of them at once. */
    private List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();

        Map<Long, Map<String, Long>> reactionCounts = new HashMap<>();
        for (Object[] row : reactionRepository.countByPostAndType(ids)) {
            // Old reactions without a type are hearts: they add up with the explicit ones.
            reactionCounts.computeIfAbsent((Long) row[0], id -> new HashMap<>())
                    .merge(row[1] != null ? (String) row[1] : DEFAULT_REACTION, (Long) row[2], Long::sum);
        }
        Map<Long, String> viewerReactions = new HashMap<>();
        for (Object[] row : reactionRepository.findTypesOfUser(ids, viewerId)) {
            viewerReactions.put((Long) row[0], row[1] != null ? (String) row[1] : DEFAULT_REACTION);
        }
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countByPostIds(ids)) {
            commentCounts.put((Long) row[0], (Long) row[1]);
        }

        return posts.stream()
                .map(post -> PostResponse.builder()
                        .id(post.getId())
                        .authorId(post.getAuthor().getId())
                        .authorUsername(post.getAuthor().getUsername())
                        .authorDisplayName(post.getAuthor().getDisplayName())
                        .authorAvatarUrl(UserCard.avatarUrl(post.getAuthor()))
                        .musicId(post.getMusicId())
                        .musicProvider(post.getMusicProvider())
                        .trackName(post.getTrackName())
                        .artistName(post.getArtistName())
                        .albumCoverUrl(post.getAlbumCoverUrl())
                        .createdAt(post.getCreatedAt())
                        .caption(post.getCaption())
                        .previewUrl(post.getPreviewUrl())
                        .appleMusicUrl(post.getAppleMusicUrl())
                        .spotifyUrl(post.getSpotifyUrl() != null ? post.getSpotifyUrl() : spotifyTrackUrl(post))
                        .reactionsCount(reactionCounts.getOrDefault(post.getId(), Map.of()))
                        .commentsCount(commentCounts.getOrDefault(post.getId(), 0L))
                        .currentUserReaction(viewerReactions.get(post.getId()))
                        .build())
                .toList();
    }

    private CommentResponse toCommentResponse(PostComment comment, Long viewerId, List<CommentResponse> replies) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorId(comment.getAuthor().getId())
                .authorUsername(comment.getAuthor().getUsername())
                .authorDisplayName(comment.getAuthor().getDisplayName())
                .authorAvatarUrl(UserCard.avatarUrl(comment.getAuthor()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .parentId(comment.getParentComment() == null ? null : topLevel(comment).getId())
                .likesCount(comment.getLikedByUsers().size())
                .isLikedByCurrentUser(comment.getLikedByUsers().stream().anyMatch(u -> u.getId().equals(viewerId)))
                .replies(replies)
                .build();
    }

    /** The top-level comment of a thread (the comment itself if it has no parent). */
    private static PostComment topLevel(PostComment comment) {
        PostComment current = comment;
        while (current.getParentComment() != null) {
            current = current.getParentComment();
        }
        return current;
    }

    private static String reactionType(PostReaction reaction) {
        return reaction.getType() != null ? reaction.getType() : DEFAULT_REACTION;
    }

    private static String normalizeReaction(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidActionException("Reaction must not be empty");
        }
        String reaction = type.strip();
        if (reaction.codePointCount(0, reaction.length()) > MAX_REACTION_LENGTH) {
            throw new InvalidActionException("Reaction is too long");
        }
        return reaction;
    }

    /** Posts made from Spotify's catalog link to their track even before the lookup ran. */
    static String spotifyTrackUrl(Post post) {
        if (post.getMusicProvider() != MusicProvider.SPOTIFY) {
            return null;
        }
        String id = post.getMusicId().replace("spotify:track:", "").strip();
        return id.matches("[A-Za-z0-9]+") ? "https://open.spotify.com/track/" + id : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
