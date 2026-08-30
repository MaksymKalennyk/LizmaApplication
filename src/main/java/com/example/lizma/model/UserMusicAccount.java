package com.example.lizma.model;

import com.example.lizma.model.enums.MusicProvider;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_music_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","provider"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserMusicAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MusicProvider provider;

    @Column(name = "spotify_refresh_token", length = 512)
    private String spotifyRefreshToken;

    @Column(name = "spotify_access_token", length = 512)
    private String spotifyAccessToken;

    @Column(name = "spotify_access_token_expires_at")
    private Instant spotifyAccessTokenExpiresAt;

    @Column(length = 512) private String appleUserToken;
    @Column(length = 8) private String appleStorefront;

    private Instant connectedAt;

    @PrePersist void onCreate() { connectedAt = Instant.now(); }
}