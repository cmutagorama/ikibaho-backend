package com.charlie.ikibaho.project.events;

import java.time.Instant;
import java.util.UUID;

public record ProjectCreated(
        UUID projectId,
        UUID organizationId,
        String key,
        String name,
        UUID leadId,
        Instant occurredAt
) {
}
