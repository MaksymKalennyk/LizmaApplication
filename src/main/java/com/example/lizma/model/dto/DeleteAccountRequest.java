package com.example.lizma.model.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank(message = "Enter your password to delete the account") String password) {
}
