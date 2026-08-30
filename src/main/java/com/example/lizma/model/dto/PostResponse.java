package com.example.lizma.model.dto;

import com.example.lizma.model.enums.MusicProvider;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PostResponse {
    private Long id;
    private Long authorId;
    private String authorUsername;
    private String musicId;
    private MusicProvider musicProvider;
    private String trackName;
    private String artistName;
    private String albumCoverUrl;
    private Instant createdAt;
    private String caption;
    
    private java.util.Map<String, Long> reactionsCount;
    private long commentsCount;
    private String currentUserReaction;
}
