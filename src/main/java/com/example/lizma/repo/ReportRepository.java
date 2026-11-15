package com.example.lizma.repo;

import com.example.lizma.model.Report;
import com.example.lizma.model.Users;
import com.example.lizma.model.enums.ReportStatus;
import com.example.lizma.model.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    boolean existsByReporterAndTargetTypeAndTargetIdAndStatus(Users reporter, ReportTargetType targetType,
                                                              Long targetId, ReportStatus status);

    List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    List<Report> findByTargetUserAndStatus(Users targetUser, ReportStatus status);
}
