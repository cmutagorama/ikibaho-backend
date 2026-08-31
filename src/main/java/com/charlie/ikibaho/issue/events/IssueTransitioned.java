package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a workflow transition commits. Carries both ends of the move:
 * "went to Done" is a much weaker audit record than "went from In Progress to
 * Done", and the from-status is unrecoverable once the row is updated.
 *
 * Status *names* travel with the event as well as ids. Statuses live inside the
 * issue module, so a consumer in another module could not resolve them without a
 * boundary violation -- and a rename should not retroactively rewrite what the
 * history says happened. The id is kept for anything that wants to follow the
 * link today.
 */
public record IssueTransitioned(
        UUID issueId,
        UUID projectId,
        String issueKey,
        UUID fromStatusId,
        String fromStatus,
        UUID toStatusId,
        String toStatus,
        UUID transitionId,
        UUID actorId,
        Instant occurredAt
) {
}
