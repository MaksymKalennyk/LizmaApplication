package com.example.lizma.controller;

import com.example.lizma.model.dto.PostRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.service.PostService;
import com.example.lizma.service.UserService;
import io.swagger.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserService userService;

    @Operation(summary = "Create a new music post")
    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request, Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        PostResponse post = postService.createPost(userId, request);
        return ResponseEntity.ok(post);
    }

    @Operation(summary = "Get music feed from friends")
    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        List<PostResponse> feed = postService.getFeed(userId, page, size);
        return ResponseEntity.ok(feed);
    }

    @Operation(summary = "React to a post with an emoji or unlike")
    @PostMapping("/{postId}/react")
    public ResponseEntity<Void> reactToPost(@PathVariable Long postId, @RequestParam String type, Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        postService.reactToPost(userId, postId, type);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Add a comment to a post")
    @PostMapping("/{postId}/comments")
    public ResponseEntity<com.example.lizma.model.dto.CommentResponse> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody com.example.lizma.model.dto.CommentRequest request,
            Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        var comment = postService.addComment(userId, postId, request);
        return ResponseEntity.ok(comment);
    }

    @Operation(summary = "Get comments for a post")
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<com.example.lizma.model.dto.CommentResponse>> getComments(
            @PathVariable Long postId,
            Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        return ResponseEntity.ok(postService.getComments(postId, userId));
    }

    @Operation(summary = "Toggle like on a comment")
    @PostMapping("/{postId}/comments/{commentId}/like")
    public ResponseEntity<Void> likeComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        postService.toggleLikeComment(userId, commentId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get reactions for a post (only allowed for the author)")
    @GetMapping("/{postId}/reactions")
    public ResponseEntity<List<com.example.lizma.model.dto.ReactionDetailResponse>> getReactions(
            @PathVariable Long postId,
            Principal principal) {
        Long userId = userService.getCurrentUserId(principal);
        return ResponseEntity.ok(postService.getReactions(userId, postId));
    }
}
