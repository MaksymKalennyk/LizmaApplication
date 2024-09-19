package com.example.lizma.model;

import com.example.lizma.model.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "friendships")
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Один з друзів
    @ManyToOne
    @JoinColumn(name = "user1_id", nullable = false)
    private Users user1;

    // Інший друг
    @ManyToOne
    @JoinColumn(name = "user2_id", nullable = false)
    private Users user2;

    // Статус дружби: CONFIRMED, BLOCKED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    // Часові мітки
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
