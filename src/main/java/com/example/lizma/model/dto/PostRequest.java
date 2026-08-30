package com.example.lizma.model.dto;

import com.example.lizma.model.enums.MusicProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostRequest {
    @NotBlank
    private String musicId;

    @NotNull
    private MusicProvider musicProvider;

    @NotBlank
    private String trackName;

    @NotBlank
    private String artistName;

    private String albumCoverUrl;

    private String caption;
}
