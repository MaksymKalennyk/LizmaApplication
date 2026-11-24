package com.example.lizma.model.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/logout}: the session to end and, optionally, the phone's push token to forget. */
public record LogoutRequest(@NotBlank(message = "refreshToken is required") String refreshToken, String deviceToken) {
}
