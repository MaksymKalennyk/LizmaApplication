package com.example.lizma.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A link shared from Apple Music or Spotify (or a text with one), and the phone's region ("UA"), whose store is
 * searched when the link doesn't name one.
 */
public record ShareLinkRequest(
        @NotBlank(message = "Share a song from Apple Music or Spotify")
        @Size(max = 2048, message = "Share a song from Apple Music or Spotify")
        String url,
        @Size(max = 8)
        String country) {
}
