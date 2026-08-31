package com.charlie.ikibaho.identity.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when self-serve signup creates an organization and its first admin.
 * Deliberately carries no password material of any kind.
 */
public record UserRegistered(
        UUID userId,
        UUID organizationId,
        String email,
        String displayName,
        Instant occurredAt
) {
}
