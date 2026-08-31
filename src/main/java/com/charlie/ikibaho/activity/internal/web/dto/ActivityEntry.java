package com.charlie.ikibaho.activity.internal.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One history line, ready to render.
 *
 * People are resolved to names here rather than stored as names on the row: a
 * user who changes their display name should appear under the new name
 * everywhere. Statuses go the other way -- those are stored as the names they
 * had at the time, because a renamed status must not rewrite what happened.
 */
public record ActivityEntry(
        UUID id,
        UUID issueId,
        String issueKey,
        String kind,
        String field,
        String oldValue,
        String newValue,
        UUID referenceId,
        Actor actor,
        Instant occurredAt
) {
    public record Actor(UUID id, String displayName, String avatarUrl) {
    }
}
