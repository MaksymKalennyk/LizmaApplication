package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.DisplayNameRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.model.dto.ProfileView;
import com.example.lizma.service.PostService;
import com.example.lizma.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PostService postService;

    @Operation(summary = "A user's profile: name, photo, how you're connected, and whether you can see their posts")
    @GetMapping("/profiles/{username}")
    public ProfileView profile(@PathVariable String username, @AuthenticationPrincipal Users me) {
        return profileService.getProfile(me, username);
    }

    @Operation(summary = "A user's posts, newest first (yours or a friend's); `before` continues after that post")
    @GetMapping("/profiles/{username}/posts")
    public List<PostResponse> posts(@PathVariable String username,
                                    @RequestParam(required = false) Long before,
                                    @RequestParam(defaultValue = "30") int size,
                                    @AuthenticationPrincipal Users me) {
        Users author = profileService.requirePostsVisible(me, username);
        return postService.getPostsOf(author, me.getId(), before, size);
    }

    @Operation(summary = "Set your display name (empty removes it)")
    @PutMapping("/me/profile")
    public ProfileView setDisplayName(@Valid @RequestBody DisplayNameRequest request, @AuthenticationPrincipal Users me) {
        return profileService.setDisplayName(me, request.displayName());
    }

    @Operation(summary = "Upload a profile photo (JPEG or PNG): the server keeps the centre square at 512×512, without metadata")
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> setAvatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Users me)
            throws IOException {
        return Map.of("avatarUrl", profileService.setAvatar(me, file.getBytes()));
    }

    @Operation(summary = "Remove your profile photo")
    @DeleteMapping("/me/avatar")
    public Map<String, String> removeAvatar(@AuthenticationPrincipal Users me) {
        profileService.removeAvatar(me);
        return Map.of("message", "Photo removed");
    }
}
