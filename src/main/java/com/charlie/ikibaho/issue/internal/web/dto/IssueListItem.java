package com.charlie.ikibaho.issue.internal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row shape for list endpoints.
 * <p>
 * Deliberately NOT IssueResponse. Reusing the detail record forced description,
 * parentId, dueDate, updatedAt and customFields to be nulled out, and a client
 * cannot tell "not included in the list projection" from "genuinely null". A
 * narrower record makes the projection part of the contract.
 */
public record IssueListItem(
        UUID id,
        String issueKey,
        String summary,
        String priority,
        UUID typeId,
        String issueType,
        UUID statusId,
        String status,
        String statusCategory,
        IssueResponse.UserRef reporter,
        IssueResponse.UserRef assignee,
        BigDecimal storyPoints,
        Instant createdAt,
        long version) {
}
