package com.charlie.ikibaho.project;

import java.util.Optional;
import java.util.UUID;

public interface ProjectService {
    ProjectSummary create(UUID organizationId, String key, String name, UUID leadId);

    Optional<ProjectSummary> findById(UUID id);

    Optional<ProjectSummary> findByKey(UUID organizationId, String key);

    /**
     * Atomically increments and returns the per-project issue counter.
     */
    long nextIssueNumber(UUID projectId);

    boolean hasRole(UUID userId, UUID projectId, String roleName);

    Optional<UUID> workflowIdFor(UUID projectId);

    /**
     * Gives a project its own workflow, overriding the organization default.
     * Module SPI: the caller is a trusted module, so there is no permission check here.
     */
    void assignWorkflow(UUID projectId, UUID workflowId);
}
