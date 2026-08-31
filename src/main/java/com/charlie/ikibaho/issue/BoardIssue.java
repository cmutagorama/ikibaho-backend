package com.charlie.ikibaho.issue;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An issue as a board or backlog needs to draw it, in rank order.
 *
 * Published by the issue module so board never touches issue tables. It carries
 * the status *name and category* alongside the id because a board column header
 * needs both, and resolving them in board would mean reading status rows that
 * the issue module owns.
 *
 * {@code rank} is never null: V10 backfilled every existing row and IssueService
 * assigns one at creation, so any two issues can always be dragged against each
 * other.
 */
public record BoardIssue(
        UUID id,
        String issueKey,
        String summary,
        String priority,
        UUID statusId,
        String status,
        String statusCategory,
        UUID typeId,
        String issueType,
        UUID assigneeId,
        UUID parentId,
        BigDecimal storyPoints,
        String rank
) {
}
