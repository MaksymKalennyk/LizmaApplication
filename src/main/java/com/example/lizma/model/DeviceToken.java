package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A phone that gets the user's push notifications: the APNs device token the app registered after sign-in.
 * A token belongs to one user at a time (the last one who signed in on that phone) and is removed on
 * sign-out, when the account goes, or when APNs says the app was deleted from the phone.
 */
@Entity
@Table(name = "device_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceToken {

    /** Development builds get tokens from the APNs sandbox, TestFlight and App Store builds from production. */
    public enum Environment { SANDBOX, PRODUCTION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Users user;

    @Column(nullable = false, unique = true, length = 200)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    @Column(nullable = false)
    private Instant createdAt;

    /** Last time the app registered it: the oldest tokens go first when a user has too many. */
    @Column(nullable = false)
    private Instant updatedAt;
}
