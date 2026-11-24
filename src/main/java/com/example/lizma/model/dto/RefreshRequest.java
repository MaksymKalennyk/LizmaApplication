package com.example.lizma.model.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/refresh} and {@code POST /auth/logout}. */
public record RefreshRequest(@NotBlank(message = "refreshToken is required") String refreshToken) {
}
