package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.internal.domain.ProjectRoleActor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRoleActorRepository extends JpaRepository<ProjectRoleActor, UUID> {
    List<ProjectRoleActor> findByProjectId(UUID projectId);

    List<ProjectRoleActor> findByProjectIdAndRoleId(UUID projectId, UUID roleId);

    boolean existsByProjectIdAndRoleIdAndUserId(UUID projectId, UUID roleId, UUID userId);

    boolean existsByProjectIdAndRoleIdAndGroupId(UUID projectId, UUID roleId, UUID groupId);

    long deleteByProjectIdAndRoleIdAndUserId(UUID projectId, UUID roleId, UUID userId);

    long deleteByProjectIdAndRoleIdAndGroupId(UUID projectId, UUID roleId, UUID groupId);

    /**
     * Guards against deleting a role that is still granting permissions somewhere.
     */
    long countByRoleId(UUID roleId);
}
