package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusRepository extends JpaRepository<Status, UUID> {
    List<Status> findByOrganizationIdOrderByPositionAsc(UUID organizationId);

    Optional<Status> findFirstByOrganizationIdOrderByPositionAsc(UUID organizationId);

    Optional<Status> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);
}
