package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.ProjectSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectQueries {
    private final JdbcClient jdbc;

    ProjectQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DeletedProjectRow(UUID id, String key, String name, Instant deletedAt) {
    }

    /**
     * The only way to see soft-deleted projects -- JPA filters them out entirely.
     */
    public List<DeletedProjectRow> findDeleted(UUID organizationId) {
        return jdbc.sql("""
                        SELECT id, key, name, deleted_at
                        FROM project
                        WHERE organization_id = :organizationId AND deleted = true
                        ORDER BY deleted_at DESC
                        """)
                .param("organizationId", organizationId)
                .query(DeletedProjectRow.class)
                .list();
    }

    /**
     * The key of a soft-deleted project. Native, because JPA cannot see deleted rows.
     */
    public Optional<String> keyOfDeletedProject(UUID projectId, UUID organizationId) {
        return jdbc.sql("""
                        SELECT key
                        FROM project
                        WHERE id = :projectId
                          AND organization_id = :organizationId
                          AND deleted = true
                        """)
                .param("projectId", projectId)
                .param("organizationId", organizationId)
                .query(String.class)
                .optional();
    }

    public List<ProjectSummary> findSummaries(Collection<UUID> ids) {
        return jdbc.sql("""
                        SELECT id, organization_id, key, name, lead_id
                        FROM project
                        WHERE id = ANY(:ids) AND deleted = false
                        ORDER BY key
                        """)
                .param("ids", ids.toArray(UUID[]::new))
                .query(ProjectSummary.class)
                .list();
    }
}
