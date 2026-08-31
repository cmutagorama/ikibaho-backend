package com.charlie.ikibaho.activity.internal.persistence;

import com.charlie.ikibaho.activity.internal.domain.IssueHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, UUID> {

    List<IssueHistory> findByIssueIdOrderByOccurredAtDesc(UUID issueId, Pageable pageable);

    List<IssueHistory> findByProjectIdOrderByOccurredAtDesc(UUID projectId, Pageable pageable);

    boolean existsByDedupeKey(String dedupeKey);
}
