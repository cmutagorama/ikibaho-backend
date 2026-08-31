package com.charlie.ikibaho.search.internal.query;

import java.util.Set;
import java.util.UUID;

/**
 * Who is asking, and what they are allowed to see.
 * <p>
 * Built by the service from PermissionService before the query is compiled, and
 * required by the compiler -- so it is not possible to compile a query that has
 * not been scoped. Making it a constructor argument rather than an optional
 * filter is the point.
 */
public record SearchContext(
        UUID currentUserId,
        UUID organizationId,
        Set<UUID> browsableProjectIds) {
}
