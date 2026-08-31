package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsBySlug(String slug);

    Optional<Organization> findBySlug(String slug);
}
