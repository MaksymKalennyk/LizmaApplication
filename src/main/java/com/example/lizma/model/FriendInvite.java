package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "friend_invites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendInvite {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(optional = false)
    private Users requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant acceptedAt;

    public enum Status { ISSUED, ACCEPTED, EXPIRED }
}
