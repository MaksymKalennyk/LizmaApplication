package com.example.lizma.controller;

import com.example.lizma.model.Users;
import com.example.lizma.model.dto.DeviceRegistration;
import com.example.lizma.model.dto.NotificationSettings;
import com.example.lizma.service.push.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/me")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Register this phone for push notifications (after every sign-in and whenever iOS gives a new token)")
    @PutMapping("/devices")
    public Map<String, String> registerDevice(@Valid @RequestBody DeviceRegistration registration,
                                              @AuthenticationPrincipal Users me) {
        notificationService.registerDevice(me, registration);
        return Map.of("message", "Device registered");
    }

    @Operation(summary = "Stop push notifications to this phone (before signing out)")
    @DeleteMapping("/devices/{token}")
    public Map<String, String> unregisterDevice(@PathVariable String token, @AuthenticationPrincipal Users me) {
        notificationService.unregisterDevice(me.getId(), token);
        return Map.of("message", "Device removed");
    }

    @Operation(summary = "What you get push notifications about")
    @GetMapping("/notifications")
    public NotificationSettings settings(@AuthenticationPrincipal Users me) {
        return notificationService.settings(me);
    }

    @Operation(summary = "Change what you get push notifications about; fields left out stay as they are")
    @PutMapping("/notifications")
    public NotificationSettings updateSettings(@RequestBody NotificationSettings change, @AuthenticationPrincipal Users me) {
        return notificationService.updateSettings(me, change);
    }
}
