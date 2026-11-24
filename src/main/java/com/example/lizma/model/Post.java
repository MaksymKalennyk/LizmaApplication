package com.example.lizma.model;

import com.example.lizma.model.enums.MusicProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    @Column(nullable = false)
    private String musicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MusicProvider musicProvider;

    @Column(nullable = false)
    private String trackName;

    @Column(nullable = false)
    private String artistName;

    @Column(length = 512)
    private String albumCoverUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 1024)
    private String caption;

    /** A 30-second preview anyone can play in the app (Apple's), when known. */
    @Column(length = 512)
    private String previewUrl;

    /** The track in Apple Music and in Spotify, for "Open in …"; looked up after posting when missing. */
    @Column(length = 512)
    private String appleMusicUrl;

    @Column(length = 512)
    private String spotifyUrl;

    /** When the other services' links were looked up (whatever the result); null until then. */
    private Instant linksResolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
