package com.charlie.ikibaho.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Note the absence of the secret. It is returned exactly once, from the create
 * call, and never again -- so a leaked read of this endpoint does not hand
 * someone the ability to forge deliveries.
 */
public record WebhookResponse(
        UUID id,
        String url,
        String description,
        List<String> eventTypes,
        boolean active,
        Instant createdAt,
        long pendingDeliveries,
        long failedDeliveries
) {
}