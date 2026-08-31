package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.internal.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRoleRepository extends JpaRepository<ProjectRole, UUID> {
    Optional<ProjectRole> findByOrganizationIdAndName(UUID organizationId, String name);

    List<ProjectRole> findByOrganizationId(UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
