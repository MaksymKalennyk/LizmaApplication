package com.example.lizma.model.dto;

import com.example.lizma.model.enums.MusicProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostRequest {
    @NotBlank(message = "Track id is required")
    @Size(max = 255, message = "Track id is too long")
    private String musicId;

    @NotNull(message = "Music provider is required")
    private MusicProvider musicProvider;

    @NotBlank(message = "Track name is required")
    @Size(max = 255, message = "Track name is too long")
    private String trackName;

    @NotBlank(message = "Artist name is required")
    @Size(max = 255, message = "Artist name is too long")
    private String artistName;

    @Size(max = 512, message = "Album cover URL is too long")
    private String albumCoverUrl;

    @Size(max = 1024, message = "Caption must be at most 1024 characters")
    private String caption;

    /** From the catalog search: the 30-second preview and the track's Apple Music page. */
    @Size(max = 512, message = "Preview URL is too long")
    private String previewUrl;

    @Size(max = 512, message = "Apple Music URL is too long")
    private String appleMusicUrl;
    /** The exact Spotify track, when the song was shared from Spotify (open.spotify.com/track/{id}). */
    @Size(max = 512, message = "Spotify URL is too long")
    private String spotifyUrl;
}
