package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.RefreshRequest;
import com.example.lizma.service.WidgetService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The home screen widget: its key, and the friends' songs it shows. */
@RestController
@RequiredArgsConstructor
public class WidgetController {

    static final String KEY_HEADER = "X-Widget-Key";

    private final WidgetService widgetService;

    @Operation(summary = "A key for this phone's home screen widget, tied to this sign-in (send its refresh token)")
    @PostMapping("/user/me/widget-key")
    public Map<String, String> issueKey(@Valid @RequestBody RefreshRequest request, @AuthenticationPrincipal Users me) {
        return Map.of("key", widgetService.issue(me, request.refreshToken()));
    }

    @Operation(summary = "Friends' latest songs for the widget, newest first (header X-Widget-Key)")
    @GetMapping("/widget/feed")
    public ResponseEntity<?> feed(@RequestHeader(value = KEY_HEADER, required = false) String key) {
        return widgetService.feed(key)
                .<ResponseEntity<?>>map(feed -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(feed))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Open Lizma to sign in")));
    }
}
