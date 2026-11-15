package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** {@code blocker} doesn't want any contact with {@code blocked}; enforced in both directions. */
@Entity
@Table(name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBlock {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Users blocker;

    @ManyToOne(optional = false)
    private Users blocked;

    @Column(nullable = false)
    private Instant createdAt;
}
