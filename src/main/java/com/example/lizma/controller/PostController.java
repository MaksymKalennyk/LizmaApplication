package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.CommentRequest;
import com.example.lizma.model.dto.CommentResponse;
import com.example.lizma.model.dto.PostRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.model.dto.ReactionDetailResponse;
import com.example.lizma.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "Create a new music post")
    @PostMapping
    public PostResponse createPost(@Valid @RequestBody PostRequest request, @AuthenticationPrincipal Users me) {
        return postService.createPost(me.getId(), request);
    }

    @Operation(summary = "Feed: your posts and your friends' posts, newest first")
    @GetMapping("/feed")
    public List<PostResponse> getFeed(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @AuthenticationPrincipal Users me) {
        return postService.getFeed(me.getId(), page, size);
    }

    @Operation(summary = "React to a post with an emoji; the same emoji again removes the reaction")
    @PostMapping("/{postId}/react")
    public void reactToPost(@PathVariable Long postId, @RequestParam String type, @AuthenticationPrincipal Users me) {
        postService.reactToPost(me.getId(), postId, type);
    }

    @Operation(summary = "Add a comment (or a reply via parentId) to a post")
    @PostMapping("/{postId}/comments")
    public CommentResponse addComment(@PathVariable Long postId,
                                      @Valid @RequestBody CommentRequest request,
                                      @AuthenticationPrincipal Users me) {
        return postService.addComment(me.getId(), postId, request);
    }

    @Operation(summary = "Get comments of a post with their replies")
    @GetMapping("/{postId}/comments")
    public List<CommentResponse> getComments(@PathVariable Long postId, @AuthenticationPrincipal Users me) {
        return postService.getComments(postId, me.getId());
    }

    @Operation(summary = "Toggle like on a comment")
    @PostMapping("/{postId}/comments/{commentId}/like")
    public void likeComment(@PathVariable Long postId, @PathVariable Long commentId, @AuthenticationPrincipal Users me) {
        postService.toggleLikeComment(me.getId(), postId, commentId);
    }

    @Operation(summary = "Who reacted to a post (only for its author)")
    @GetMapping("/{postId}/reactions")
    public List<ReactionDetailResponse> getReactions(@PathVariable Long postId, @AuthenticationPrincipal Users me) {
        return postService.getReactions(me.getId(), postId);
    }
}
