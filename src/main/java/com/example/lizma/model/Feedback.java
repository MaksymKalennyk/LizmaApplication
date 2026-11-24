package com.example.lizma.model;

import com.example.lizma.model.enums.FeedbackCategory;
import com.example.lizma.model.enums.FeedbackStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An idea, a problem or anything else a user sent from the app; read by moderators. */
@Entity
@Table(name = "feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Users author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackCategory category;

    @Column(nullable = false, length = 2000)
    private String message;

    /** Optional: where the user wants the answer to go. Used as Reply-To of the notification email. */
    @Column(length = 254)
    private String contactEmail;

    @Column(length = 32)
    private String appVersion;

    @Column(length = 32)
    private String osVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant handledAt;

    @ManyToOne
    private Users handledBy;

    /** When it went out in a notification email; null until then. */
    private Instant notifiedAt;

    /** Screenshots the author attached, oldest first (see Attachment). */
    @OneToMany(mappedBy = "feedback")
    @OrderBy("createdAt")
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();
}
