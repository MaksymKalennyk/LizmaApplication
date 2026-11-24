package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A profile photo: a 512×512 JPEG made by the server, public under /avatars/{token}.jpg. */
@Entity
@Table(name = "user_avatars")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAvatar {

    @Id
    @Column(length = 40)
    private String token;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "mediumblob")
    private byte[] image;

    @Column(nullable = false)
    private Instant createdAt;
}
