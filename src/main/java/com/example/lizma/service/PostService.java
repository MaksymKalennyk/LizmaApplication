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
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostReactionRepository;
import com.example.lizma.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    public PostResponse createPost(Long authorId, PostRequest request) {
        contentFilter.check(request.getCaption());
        Users author = userService.getById(authorId);
        Post post = Post.builder()
                .author(author)
                .musicId(request.getMusicId().strip())
                .musicProvider(request.getMusicProvider())
                .trackName(request.getTrackName().strip())
                .artistName(request.getArtistName().strip())
                .albumCoverUrl(blankToNull(request.getAlbumCoverUrl()))
                .caption(blankToNull(request.getCaption()))
                .build();
        return toResponse(postRepository.save(post), author);
    }

    /** Posts of the user's friends and the user's own posts, newest first. */
    @Transactional(readOnly = true)
    public List<PostResponse> getFeed(Long userId, int page, int size) {
        Users viewer = userService.getById(userId);
        List<Users> authors = new ArrayList<>(friendshipService.getFriends(viewer));
        authors.add(viewer);

        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return postRepository.findByAuthorInOrderByCreatedAtDesc(authors, pageable).stream()
                .map(post -> toResponse(post, viewer))
                .toList();
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
                () -> reactionRepository.save(PostReaction.builder().post(post).user(user).type(reaction).build())
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
                        .type(reactionType(reaction))
                        .createdAt(reaction.getCreatedAt())
                        .build())
                .toList();
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

    private PostResponse toResponse(Post post, Users viewer) {
        List<PostReaction> reactions = reactionRepository.findByPost(post);
        Map<String, Long> reactionCounts = reactions.stream()
                .collect(Collectors.groupingBy(PostService::reactionType, Collectors.counting()));
        String viewerReaction = reactions.stream()
                .filter(reaction -> reaction.getUser().getId().equals(viewer.getId()))
                .map(PostService::reactionType)
                .findFirst()
                .orElse(null);

        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .musicId(post.getMusicId())
                .musicProvider(post.getMusicProvider())
                .trackName(post.getTrackName())
                .artistName(post.getArtistName())
                .albumCoverUrl(post.getAlbumCoverUrl())
                .createdAt(post.getCreatedAt())
                .caption(post.getCaption())
                .reactionsCount(reactionCounts)
                .commentsCount(commentRepository.countByPost(post))
                .currentUserReaction(viewerReaction)
                .build();
    }

    private CommentResponse toCommentResponse(PostComment comment, Long viewerId, List<CommentResponse> replies) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorId(comment.getAuthor().getId())
                .authorUsername(comment.getAuthor().getUsername())
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
