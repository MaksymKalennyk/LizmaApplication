package com.example.lizma.model;

import com.example.lizma.model.enums.ReportAction;
import com.example.lizma.model.enums.ReportReason;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A user's complaint about a post, a comment or another user, waiting for a moderator. */
@Entity
@Table(name = "reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Users reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    /** Author of the reported post/comment, or the reported user. */
    @ManyToOne
    private Users targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportReason reason;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    /** What the moderator decided (null while open). */
    @Enumerated(EnumType.STRING)
    private ReportAction resolution;

    /** Who decided; null while open or if that moderator's account is gone. */
    @ManyToOne
    private Users resolvedBy;

    /** When it went out in a notification email; null until then. */
    private Instant notifiedAt;

    /** Screenshots the reporter attached, oldest first (see Attachment). */
    @OneToMany(mappedBy = "report")
    @OrderBy("createdAt")
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();
}
