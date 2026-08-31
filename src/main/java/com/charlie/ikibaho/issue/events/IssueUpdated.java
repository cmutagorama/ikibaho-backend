package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an issue's own fields change -- summary, description, type,
 * priority, estimate, due date.
 * <p>
 * Carries only identifiers. Unlike the phase 6 events, whose consumers record
 * what happened, this one exists to tell an index that a row is stale; the
 * indexer then re-reads the whole issue. Putting every field on the event would
 * mean growing the event every time the issue grows a column.
 */

public record IssueUpdated(
        UUID issueId,
        UUID projectId,
        String issueKey,
        UUID actorId,
        Instant occurredAt) {
}
