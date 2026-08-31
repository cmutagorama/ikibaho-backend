package com.charlie.ikibaho.project.internal.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class PermissionQueries {
    private final JdbcClient jdbc;

    PermissionQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Role IDs the user holds on a project, directly or via group membership.
     */
    public Set<UUID> roleIdsFor(UUID projectId, UUID userId) {
        List<UUID> ids = jdbc.sql("""
                        SELECT DISTINCT pra.role_id
                        FROM project_role_actor pra
                        LEFT JOIN group_member gm ON gm.group_id = pra.group_id
                        WHERE pra.project_id = :projectId
                          AND (pra.user_id = :userId OR gm.user_id = :userId)
                        """)
                .param("projectId", projectId)
                .param("userId", userId)
                .query(UUID.class)
                .list();
        return new HashSet<>(ids);
    }

    public Set<UUID> groupIdsFor(UUID userId) {
        List<UUID> ids = jdbc.sql("SELECT group_id FROM group_member WHERE user_id = :userId")
                .param("userId", userId)
                .query(UUID.class)
                .list();
        return new HashSet<>(ids);
    }

    /**
     * Every project in the org where the user holds BROWSE_PROJECT.
     * Evaluated entirely in SQL -- loading all projects and filtering in Java
     * would not scale and would break pagination downstream.
     */
    public Set<UUID> browsableProjectIds(UUID organizationId, UUID userId) {
        List<UUID> ids = jdbc.sql("""
                SELECT DISTINCT p.id
                FROM project p
                JOIN permission_grant pg
                     ON pg.scheme_id = p.permission_scheme_id
                    AND pg.permission = 'BROWSE_PROJECT'
                LEFT JOIN project_role_actor pra
                     ON pg.holder_type = 'PROJECT_ROLE'
                    AND pra.project_id = p.id
                    AND pra.role_id = pg.holder_ref
                LEFT JOIN group_member gm_role  ON gm_role.group_id  = pra.group_id
                LEFT JOIN group_member gm_grant ON gm_grant.group_id = pg.holder_ref
                WHERE p.organization_id = :organizationId
                  AND p.deleted = false
                  AND (
                        pg.holder_type = 'ANY_LOGGED_IN'
                     OR (pg.holder_type = 'PROJECT_LEAD' AND p.lead_id = :userId)
                     OR (pg.holder_type = 'USER'         AND pg.holder_ref = :userId)
                     OR (pg.holder_type = 'GROUP'        AND gm_grant.user_id = :userId)
                     OR (pg.holder_type = 'PROJECT_ROLE' AND (pra.user_id = :userId OR gm_role.user_id = :userId))
                  )
                """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .query(UUID.class)
                .list();
        return new HashSet<>(ids);
    }
}
