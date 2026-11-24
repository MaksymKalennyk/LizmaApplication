package com.example.lizma.repo;

import com.example.lizma.model.Attachment;
import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    /** Uploaded but not sent with a report or feedback yet. */
    @Query("select count(a) from Attachment a where a.owner = :owner and a.report is null and a.feedback is null")
    long countUnsent(@Param("owner") Users owner);

    long countByOwnerAndCreatedAtAfter(Users owner, Instant since);

    @Modifying
    @Query("delete from Attachment a where a.report is null and a.feedback is null and a.createdAt < :before")
    int deleteUnsentBefore(@Param("before") Instant before);
}
