package com.example.lizma.model.dto;

import com.example.lizma.model.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The phone's APNs device token (hex) and where it came from: SANDBOX for development builds,
 * PRODUCTION (the default) for TestFlight and the App Store.
 */
public record DeviceRegistration(
        @NotBlank(message = "Device token is required")
        @Pattern(regexp = "[0-9a-fA-F]{32,200}", message = "This isn't a device token")
        String token,
        DeviceToken.Environment environment) {
}
