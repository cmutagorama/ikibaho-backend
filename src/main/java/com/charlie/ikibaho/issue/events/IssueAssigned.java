package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an issue's assignee changes -- whether by an explicit assign
 * call or by a workflow post-function.
 * <p>
 * {@code assigneeId} is null for an unassignment, and {@code previousAssigneeId}
 * is null when the issue had no assignee. Notification cares about the
 * difference: being unassigned is worth telling someone about, and re-notifying
 * an assignee who did not actually change is not.
 */
public record IssueAssigned(
        UUID issueId,
        UUID projectId,
        String issueKey,
        String summary,
        UUID previousAssigneeId,
        UUID assigneeId,
        UUID actorId,
        Instant occurredAt
) {
}
