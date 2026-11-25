package com.example.lizma.controller;

import com.example.lizma.model.ExtensionKey;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.PostRequest;
import com.example.lizma.model.dto.PostResponse;
import com.example.lizma.model.dto.RefreshRequest;
import com.example.lizma.model.dto.ShareLinkRequest;
import com.example.lizma.model.dto.ShareResolution;
import com.example.lizma.service.ExtensionKeyService;
import com.example.lizma.service.PostService;
import com.example.lizma.service.share.SharedLinkResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Posting a song shared from Apple Music or Spotify: from the share extension (with its own key, header
 * X-Share-Key), or pasted into the composer (signed in as usual).
 */
@RestController
@RequiredArgsConstructor
public class ShareController {

    static final String KEY_HEADER = "X-Share-Key";

    private final ExtensionKeyService extensionKeys;
    private final SharedLinkResolver resolver;
    private final PostService postService;

    @Operation(summary = "A key for this phone's share extension, tied to this sign-in (send its refresh token)")
    @PostMapping("/user/me/share-key")
    public Map<String, String> issueKey(@Valid @RequestBody RefreshRequest request, @AuthenticationPrincipal Users me) {
        return Map.of("key", extensionKeys.issue(me, request.refreshToken(), ExtensionKey.Purpose.SHARE,
                "Sign in again to share songs from other apps"));
    }

    @Operation(summary = "The song a shared Apple Music or Spotify link points at (header X-Share-Key)")
    @PostMapping("/share/resolve")
    public ShareResolution resolve(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                   @Valid @RequestBody ShareLinkRequest request) {
        return resolver.resolve(owner(key).getId(), request.url(), request.country());
    }

    @Operation(summary = "Post a song found from a shared link (header X-Share-Key)")
    @PostMapping("/share/posts")
    public PostResponse post(@RequestHeader(value = KEY_HEADER, required = false) String key,
                             @Valid @RequestBody PostRequest request) {
        return postService.createPost(owner(key).getId(), request);
    }

    @Operation(summary = "The song an Apple Music or Spotify link pasted into the composer points at")
    @PostMapping("/posts/resolve-link")
    public ShareResolution resolvePasted(@Valid @RequestBody ShareLinkRequest request, @AuthenticationPrincipal Users me) {
        return resolver.resolve(me.getId(), request.url(), request.country());
    }

    private Users owner(String key) {
        return extensionKeys.authenticate(key, ExtensionKey.Purpose.SHARE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Open Lizma to sign in"));
    }
}
