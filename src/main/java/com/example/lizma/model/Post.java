package com.example.lizma.model;

import com.example.lizma.model.enums.MusicProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Users author;

    @Column(nullable = false)
    private String musicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MusicProvider musicProvider;

    @Column(nullable = false)
    private String trackName;

    @Column(nullable = false)
    private String artistName;

    @Column(length = 512)
    private String albumCoverUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 1024)
    private String caption;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
