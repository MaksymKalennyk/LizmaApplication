package com.example.lizma.repo;

import com.example.lizma.model.Feedback;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.FeedbackStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status, Pageable pageable);

    long countByStatus(FeedbackStatus status);

    long countByAuthorAndCreatedAtAfter(Users author, Instant since);

    List<Feedback> findByNotifiedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
