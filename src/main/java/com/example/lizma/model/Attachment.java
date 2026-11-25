package com.example.lizma.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A screenshot for a report or feedback: a JPEG made by the server (see UploadedImages), uploaded before the
 * report or feedback is sent and linked to it then. Only moderators can open it, under
 * /admin/attachments/{token}.jpg.
 */
@Entity
@Table(name = "attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attachment {

    @Id
    @Column(length = 40)
    private String token;

    /** Who uploaded it: only they can attach it. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Users owner;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "mediumblob")
    private byte[] image;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    private Feedback feedback;
}
