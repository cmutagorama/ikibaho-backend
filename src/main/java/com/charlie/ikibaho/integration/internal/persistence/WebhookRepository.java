package com.charlie.ikibaho.integration.internal.persistence;

import com.charlie.ikibaho.integration.internal.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<Webhook> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
