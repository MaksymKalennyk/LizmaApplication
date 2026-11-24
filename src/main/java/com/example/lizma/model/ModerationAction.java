package com.example.lizma.model;

import com.example.lizma.model.enums.ModerationActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One entry of the moderation log: who did what to whom. The target is kept by name, so the entry
 * survives the deletion of that account; the moderator becomes null if their own account goes.
 */
@Entity
@Table(name = "moderation_actions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModerationAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Users moderator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationActionType action;

    @Column(length = 50)
    private String targetUsername;

    private Long reportId;

    @Column(nullable = false)
    private Instant createdAt;
}
