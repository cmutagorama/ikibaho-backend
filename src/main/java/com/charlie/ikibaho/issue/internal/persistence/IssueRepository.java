package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {
    Optional<Issue> findByIssueKey(String issueKey);

    boolean existsByParentId(UUID parentId);

    long countByProjectId(UUID projectId);
}
