package com.charlie.ikibaho.workflow.internal.persistence;

import com.charlie.ikibaho.workflow.internal.domain.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    Optional<Workflow> findByOrganizationIdAndDefaultWorkflowTrue(UUID organizationId);

    Optional<Workflow> findByOrganizationIdAndName(UUID organizationId, String name);

    boolean existsByOrganizationId(UUID organizationId);
}
