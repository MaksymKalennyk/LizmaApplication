package com.example.lizma.model.dto;

import jakarta.validation.constraints.Size;

/** {@code PUT /user/me/profile}: an empty or blank name removes it, the username is shown instead. */
public record DisplayNameRequest(
        @Size(max = 50, message = "The name can be at most 50 characters long") String displayName) {
}
