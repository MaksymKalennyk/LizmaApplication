package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * What the home screen widget reads friends' latest songs with: a key tied to one sign-in (the refresh token
 * family of that phone), valid only while that session lives. Only its SHA-256 is stored.
 */
@Entity
@Table(name = "widget_keys")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WidgetKey {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Users user;

    /** The sign-in it belongs to (see RefreshToken.familyId). */
    @Column(nullable = false, unique = true, length = 36)
    private String familyId;

    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(nullable = false)
    private Instant createdAt;

    /** Last time the widget fetched with it (updated at most hourly); null until the widget is first shown. */
    private Instant lastUsedAt;
}
