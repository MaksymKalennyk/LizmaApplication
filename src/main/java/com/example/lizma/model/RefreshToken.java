package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A long-lived token the app trades for a new short-lived JWT. Only its SHA-256 is stored, so a leaked
 * database doesn't leak sessions. Every refresh replaces it (rotation); all tokens of one sign-in share
 * {@link #familyId}, which lets a reused old token revoke the whole session.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Users user;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
}
