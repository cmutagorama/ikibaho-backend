package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    Optional<UserGroup> findByOrganizationIdAndName(UUID organizationId, String name);

    List<UserGroup> findByOrganizationId(UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
