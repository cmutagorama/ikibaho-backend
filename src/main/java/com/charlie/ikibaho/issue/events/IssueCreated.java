package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an issue is persisted.
 *
 * Every event in this package is deliberately self-contained. The publication
 * registry serializes it to JSON and may replay it minutes later -- possibly
 * after a restart, and always outside the originating transaction. A listener
 * that had to re-read the issue to learn its key would break on a replay that
 * follows a delete, and would report the issue's *current* state rather than
 * its state when the thing happened. So the payload carries what listeners need.
 */
public record IssueCreated(
        UUID issueId,
        UUID projectId,
        UUID organizationId,
        String issueKey,
        String summary,
        UUID reporterId,
        UUID assigneeId,
        Instant occurredAt
) {
}
