package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.project.internal.domain.PermissionScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionSchemeRepository extends JpaRepository<PermissionScheme, UUID> {
    Optional<PermissionScheme> findByOrganizationIdAndDefaultSchemeTrue(UUID organizationId);

    Optional<PermissionScheme> findByOrganizationIdAndName(UUID organizationId, String name);

    List<PermissionScheme> findByOrganizationId(UUID organizationId);
}
