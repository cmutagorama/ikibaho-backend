package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an issue is deleted.
 * <p>
 * The delete is soft, but consumers should treat it as final: a soft-deleted
 * issue must not appear in search results, and leaving it indexed would leak
 * summaries of deleted work through the one screen that searches everything.
 */

public record IssueDeleted(
        UUID issueId,
        UUID projectId,
        String issueKey,
        UUID actorId,
        Instant occurredAt
) {
}
