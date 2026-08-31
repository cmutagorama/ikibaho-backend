package com.charlie.ikibaho.project.internal.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Read side for the members screen. One query joins roles, actors, users and groups
 * -- the alternative (roles, then actors per role, then a user per actor) is the
 * N+1 shape this project avoids on every list endpoint.
 */
@Repository
public class ProjectRoleQueries {

    private final JdbcClient jdbc;

    ProjectRoleQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Record components match the selected column names exactly -- SimplePropertyRowMapper
     * maps by name, so an alias mismatch fails at runtime, not compile time.
     */
    public record RoleActorRow(
            UUID roleId, String roleName, String roleDescription,
            UUID actorId, UUID userId, String userDisplayName, String userEmail,
            String userAvatarUrl, UUID groupId, String groupName) { }

    public List<RoleActorRow> findRolesWithActors(UUID projectId, UUID organizationId) {
        return jdbc.sql("""
                SELECT r.id            AS role_id,
                       r.name          AS role_name,
                       r.description   AS role_description,
                       a.id            AS actor_id,
                       u.id            AS user_id,
                       u.display_name  AS user_display_name,
                       u.email         AS user_email,
                       u.avatar_url    AS user_avatar_url,
                       g.id            AS group_id,
                       g.name          AS group_name
                FROM project_role r
                LEFT JOIN project_role_actor a
                       ON a.role_id = r.id AND a.project_id = :projectId
                LEFT JOIN app_user   u ON u.id = a.user_id
                LEFT JOIN user_group g ON g.id = a.group_id
                WHERE r.organization_id = :organizationId
                ORDER BY r.name, u.display_name NULLS FIRST, g.name
                """)
                .param("projectId", projectId)
                .param("organizationId", organizationId)
                .query(RoleActorRow.class)
                .list();
    }
}
