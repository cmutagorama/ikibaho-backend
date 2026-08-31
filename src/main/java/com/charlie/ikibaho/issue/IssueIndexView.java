package com.charlie.ikibaho.issue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything an external index needs about one issue, in one read.
 * <p>
 * Published so that search can rebuild its own row without touching issue
 * tables. It is deliberately flat and denormalized -- status and type arrive as
 * names, comments as one concatenated blob -- because the consumer's job is to
 * turn this into a single searchable document, not to re-join it.
 */
public record IssueIndexView(
        UUID issueId,
        UUID organizationId,
        UUID projectId,
        String projectKey,
        String issueKey,
        long issueNumber,
        String summary,
        String description,
        UUID typeId,
        String typeName,
        UUID statusId,
        String statusName,
        String statusCategory,
        String priority,
        UUID assigneeId,
        UUID reporterId,
        UUID parentId,
        BigDecimal storyPoints,
        LocalDate dueDate,
        String rank,
        /** Every comment on the issue, concatenated -- searchable, never displayed. */
        String commentText,
        Instant createdAt,
        Instant updatedAt
) {
}
