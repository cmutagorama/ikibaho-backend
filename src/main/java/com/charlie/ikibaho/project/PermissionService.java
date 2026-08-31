package com.charlie.ikibaho.project;


import java.util.Set;
import java.util.UUID;

public interface PermissionService {
    boolean hasPermission(UUID userId, UUID projectId, Permission permission);

    boolean hasPermission(UUID userId, IssueContext issue, Permission permission);

    Set<Permission> permissionsFor(UUID userId, UUID projectId);

    /**
     * Throws NotFoundException if the user cannot browse, ForbiddenException otherwise.
     */
    void require(UUID userId, UUID projectId, Permission permission);

    /**
     * Every list query pushes this into its WHERE clause.
     */
    Set<UUID> browsableProjectIds(UUID userId);

    void require(UUID userId, IssueContext issue, Permission permission);

    Set<Permission> permissionsFor(UUID userId, IssueContext issue);
}
