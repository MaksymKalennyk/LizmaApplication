package com.example.lizma.repo;

import com.example.lizma.model.ModerationAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    List<ModerationAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
