package com.example.lizma.service;

import com.example.lizma.model.ModerationAction;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.ModerationLogEntry;
import com.example.lizma.model.enums.ModerationActionType;
import com.example.lizma.repo.ModerationActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** What moderators did, newest first: with two or more moderators everyone sees who handled what. */
@Service
@RequiredArgsConstructor
public class ModerationLogService {

    private static final int MAX_ENTRIES = 200;

    private final ModerationActionRepository repository;

    @Transactional
    public void record(Users moderator, ModerationActionType action, String targetUsername, Long reportId) {
        repository.save(ModerationAction.builder()
                .moderator(moderator)
                .action(action)
                .targetUsername(targetUsername)
                .reportId(reportId)
                .createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<ModerationLogEntry> recent(int limit) {
        int size = limit <= 0 || limit > MAX_ENTRIES ? MAX_ENTRIES : limit;
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(entry -> new ModerationLogEntry(
                        entry.getId(),
                        entry.getAction(),
                        entry.getModerator() != null ? entry.getModerator().getUsername() : null,
                        entry.getTargetUsername(),
                        entry.getReportId(),
                        entry.getCreatedAt()))
                .toList();
    }
}
