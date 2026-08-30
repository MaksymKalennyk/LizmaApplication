package com.example.lizma.service;

import com.example.lizma.model.Friendship;
import com.example.lizma.model.Post;
import com.example.lizma.model.PostReaction;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.CommentResponse;
import com.example.lizma.model.dto.PostRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.model.enums.FriendshipStatus;
import com.example.lizma.repo.FriendshipRepository;
import com.example.lizma.repo.PostCommentRepository;
import com.example.lizma.repo.PostReactionRepository;
import com.example.lizma.repo.PostRepository;
import com.example.lizma.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostReactionRepository reactionRepository;
    private final PostCommentRepository commentRepository;

    public PostResponse createPost(Long authorId, PostRequest request) {
        Users author = userRepository.findById(authorId).orElseThrow(() -> new RuntimeException("User not found"));

        Post post = Post.builder()
                .author(author)
                .musicId(request.getMusicId())
                .musicProvider(request.getMusicProvider())
                .trackName(request.getTrackName())
                .artistName(request.getArtistName())
                .albumCoverUrl(request.getAlbumCoverUrl())
                .caption(request.getCaption())
                .build();

        Post saved = postRepository.save(post);
        return mapToResponse(saved, author);
    }

    public List<PostResponse> getFeed(Long userId, int page, int size) {
        Users currentUser = userRepository.findById(userId).orElseThrow();

        // Find friends
        List<Friendship> friendships = friendshipRepository.findByUser1AndStatusOrUser2AndStatus(
                currentUser, FriendshipStatus.CONFIRMED,
                currentUser, FriendshipStatus.CONFIRMED
        );

        List<Users> friends = new ArrayList<>();
        for (Friendship f : friendships) {
            if (f.getUser1().equals(currentUser)) {
                friends.add(f.getUser2());
            } else {
                friends.add(f.getUser1());
            }
        }
        
        // Include self
        friends.add(currentUser);

        List<Post> posts = postRepository.findByAuthorInOrderByCreatedAtDesc(friends, PageRequest.of(page, size));
        return posts.stream().map(p -> mapToResponse(p, currentUser)).collect(Collectors.toList());
    }

    public void reactToPost(Long userId, Long postId, String type) {
        Users u = userRepository.findById(userId).orElseThrow();
        Post p = postRepository.findById(postId).orElseThrow();

        if (u.getId().equals(p.getAuthor().getId())) {
            throw new IllegalArgumentException("Cannot react to your own post");
        }

        reactionRepository.findByPostAndUser(p, u).ifPresentOrElse(
                existing -> {
                    if (existing.getType().equals(type)) {
                        // User clicked the same emoji -> remove it
                        reactionRepository.delete(existing);
                    } else {
                        // User changed emoji
                        existing.setType(type);
                        reactionRepository.save(existing);
                    }
                },
                () -> reactionRepository.save(PostReaction.builder().post(p).user(u).type(type).build())
        );
    }

    public CommentResponse addComment(Long userId, Long postId, com.example.lizma.model.dto.CommentRequest req) {
        Users u = userRepository.findById(userId).orElseThrow();
        Post p = postRepository.findById(postId).orElseThrow();
        
        com.example.lizma.model.PostComment parent = null;
        if (req.getParentId() != null) {
            parent = commentRepository.findById(req.getParentId()).orElseThrow(() -> new IllegalArgumentException("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this post");
            }
        }
        
        com.example.lizma.model.PostComment comment = com.example.lizma.model.PostComment.builder()
                .post(p)
                .author(u)
                .content(req.getContent())
                .parentComment(parent)
                .build();
                
        com.example.lizma.model.PostComment saved = commentRepository.save(comment);
        return CommentResponse.builder()
                .id(saved.getId())
                .authorId(u.getId())
                .authorUsername(u.getUsername())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .parentId(parent != null ? parent.getId() : null)
                .likesCount(0)
                .isLikedByCurrentUser(false)
                .replies(new java.util.ArrayList<>())
                .build();
    }

    public List<CommentResponse> getComments(Long postId, Long currentUserId) {
        Post p = postRepository.findById(postId).orElseThrow();
        List<com.example.lizma.model.PostComment> allComments = commentRepository.findByPostOrderByCreatedAtAsc(p);
        
        java.util.Map<Long, List<com.example.lizma.model.PostComment>> repliesMap = allComments.stream()
            .filter(c -> c.getParentComment() != null)
            .collect(Collectors.groupingBy(c -> c.getParentComment().getId()));

        return allComments.stream()
            .filter(c -> c.getParentComment() == null)
            .map(c -> mapCommentToResponse(c, currentUserId, repliesMap))
            .collect(Collectors.toList());
    }

    private CommentResponse mapCommentToResponse(com.example.lizma.model.PostComment c, Long currentUserId, java.util.Map<Long, List<com.example.lizma.model.PostComment>> repliesMap) {
        boolean isLiked = currentUserId != null && c.getLikedByUsers().stream().anyMatch(u -> u.getId().equals(currentUserId));
        
        List<CommentResponse> replies = repliesMap.getOrDefault(c.getId(), new java.util.ArrayList<>())
            .stream()
            // Map replies (not going deeper than 1 level for now to keep it simple and flat like IG)
            .map(r -> {
                boolean rIsLiked = currentUserId != null && r.getLikedByUsers().stream().anyMatch(u -> u.getId().equals(currentUserId));
                return CommentResponse.builder()
                    .id(r.getId())
                    .authorId(r.getAuthor().getId())
                    .authorUsername(r.getAuthor().getUsername())
                    .content(r.getContent())
                    .createdAt(r.getCreatedAt())
                    .parentId(c.getId())
                    .likesCount(r.getLikedByUsers().size())
                    .isLikedByCurrentUser(rIsLiked)
                    .replies(new java.util.ArrayList<>()) // Replies don't have replies
                    .build();
            })
            .collect(Collectors.toList());

        return CommentResponse.builder()
            .id(c.getId())
            .authorId(c.getAuthor().getId())
            .authorUsername(c.getAuthor().getUsername())
            .content(c.getContent())
            .createdAt(c.getCreatedAt())
            .parentId(null)
            .likesCount(c.getLikedByUsers().size())
            .isLikedByCurrentUser(isLiked)
            .replies(replies)
            .build();
    }

    public void toggleLikeComment(Long userId, Long commentId) {
        Users u = userRepository.findById(userId).orElseThrow();
        com.example.lizma.model.PostComment c = commentRepository.findById(commentId).orElseThrow();
        
        if (c.getLikedByUsers().contains(u)) {
            c.getLikedByUsers().remove(u);
        } else {
            c.getLikedByUsers().add(u);
        }
        commentRepository.save(c);
    }

    public List<com.example.lizma.model.dto.ReactionDetailResponse> getReactions(Long userId, Long postId) {
        Post p = postRepository.findById(postId).orElseThrow();
        
        if (!p.getAuthor().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the author can view reaction details");
        }
        
        return reactionRepository.findByPost(p).stream().map(r ->
            com.example.lizma.model.dto.ReactionDetailResponse.builder()
                .authorId(r.getUser().getId())
                .authorUsername(r.getUser().getUsername())
                .type(r.getType())
                .createdAt(r.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }

    private PostResponse mapToResponse(Post post, Users currentUser) {
        List<PostReaction> reactions = reactionRepository.findByPost(post);
        java.util.Map<String, Long> reactionCounts = reactions.stream()
                .collect(Collectors.groupingBy(r -> r.getType() != null ? r.getType() : "❤️", Collectors.counting()));
                
        long comments = commentRepository.countByPost(post);
        
        String myReaction = null;
        if (currentUser != null) {
            myReaction = reactions.stream()
                .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                .map(r -> r.getType() != null ? r.getType() : "❤️")
                .findFirst()
                .orElse(null);
        }

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
                .commentsCount(comments)
                .currentUserReaction(myReaction)
                .build();
    }
}
