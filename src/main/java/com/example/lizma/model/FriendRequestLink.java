package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "friend_request_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRequestLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Унікальний токен для посилання
    @Column(unique = true, nullable = false)
    private String token;

    // Ініціатор запрошення
    @ManyToOne
    @JoinColumn(name = "requester_id", nullable = false)
    private Users requester;

    // Дата закінчення дії посилання (необов'язково)
    private LocalDateTime expiryDate;
}
