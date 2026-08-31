package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomFieldRepository extends JpaRepository<CustomFieldDefinition, UUID> {
    /**
     * Org-wide fields plus fields scoped to this project.
     */
    @Query("""
            SELECT f FROM CustomFieldDefinition f
            WHERE f.organizationId = :organizationId
              AND (f.projectId IS NULL OR f.projectId = :projectId)
            """)
    List<CustomFieldDefinition> findApplicable(@Param("organizationId") UUID organizationId,
                                               @Param("projectId") UUID projectId);
}
