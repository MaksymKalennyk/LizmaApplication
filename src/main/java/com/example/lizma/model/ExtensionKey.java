package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * What an app extension uses instead of the user's session: a key tied to one sign-in (the refresh token family of
 * that phone), valid only while that session lives. The home screen widget reads friends' latest songs with one,
 * the share extension posts a song with another. Only its SHA-256 is stored.
 */
@Entity
@Table(name = "extension_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_extension_keys_family_purpose", columnNames = {"family_id", "purpose"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtensionKey {

    /** What a key may do; a sign-in has at most one key of each. */
    public enum Purpose {
        /** Read friends' latest songs (the home screen widget). */
        WIDGET,
        /** Find a song shared from Apple Music or Spotify and post it (the share extension). */
        SHARE
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Users user;

    /** The sign-in it belongs to (see RefreshToken.familyId). */
    @Column(nullable = false, length = 36)
    private String familyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(nullable = false)
    private Instant createdAt;

    /** Last time it was used (updated at most hourly); null until then. */
    private Instant lastUsedAt;
}
