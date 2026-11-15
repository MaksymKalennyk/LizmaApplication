package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single-use invite link {@code <base url>/i/<token>}: whoever opens it (and is signed in) becomes
 * a friend of {@link #requester}. The token is a random secret, so links can't be guessed.
 */
@Entity
@Table(name = "friend_invites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendInvite {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** Who created and shared the invite. */
    @ManyToOne(optional = false)
    private Users requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant acceptedAt;

    /** Who used the invite. */
    @ManyToOne
    private Users acceptedBy;

    public enum Status { ISSUED, ACCEPTED, EXPIRED }

    public boolean isExpiredAt(Instant moment) {
        return status == Status.EXPIRED || expiresAt.isBefore(moment);
    }
}
