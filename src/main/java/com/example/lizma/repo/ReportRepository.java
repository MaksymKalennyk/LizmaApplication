package com.example.lizma.repo;

import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    boolean existsByReporterAndTargetTypeAndTargetIdAndStatus(Users reporter, ReportTargetType targetType,
                                                              Long targetId, ReportStatus status);

    List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    List<Report> findByTargetUserAndStatus(Users targetUser, ReportStatus status);

    /** Handled reports, the most recently handled first. */
    List<Report> findByStatusInOrderByResolvedAtDesc(Collection<ReportStatus> statuses, Pageable pageable);

    long countByStatus(ReportStatus status);

    long countByReporterAndCreatedAtAfter(Users reporter, Instant since);

    List<Report> findByNotifiedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
