package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueTypeRepository extends JpaRepository<IssueType, UUID> {
    List<IssueType> findByOrganizationIdOrderByHierarchyLevelDescNameAsc(UUID organizationId);

    Optional<IssueType> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
