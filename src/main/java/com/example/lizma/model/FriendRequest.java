package com.example.lizma.model;

import com.example.lizma.model.enums.FriendRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "friend_requests")
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Користувач, який відправив запит
    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    private Users requester;

    // Користувач, який отримав запит
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private Users recipient;

    // Статус запиту: PENDING, ACCEPTED, DECLINED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendRequestStatus status;

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
