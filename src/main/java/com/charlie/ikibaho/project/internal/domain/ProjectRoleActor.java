package com.charlie.ikibaho.project.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "project_role_actor")
public class ProjectRoleActor extends BaseEntity {
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    protected ProjectRoleActor() {
    }

    private ProjectRoleActor(UUID projectId, UUID roleId, UUID userId, UUID groupId) {
        this.projectId = projectId;
        this.roleId = roleId;
        this.userId = userId;
        this.groupId = groupId;
    }

    public static ProjectRoleActor forUser(UUID projectId, UUID roleId, UUID userId) {
        return new ProjectRoleActor(projectId, roleId, userId, null);
    }

    public static ProjectRoleActor forGroup(UUID projectId, UUID roleId, UUID groupId) {
        return new ProjectRoleActor(projectId, roleId, null, groupId);
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }
}
