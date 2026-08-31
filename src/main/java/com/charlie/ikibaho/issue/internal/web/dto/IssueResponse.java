package com.charlie.ikibaho.issue.internal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record IssueResponse(
        UUID id, String issueKey, UUID projectId,
        String summary, String description, String priority,
        UUID typeId, String issueType,
        UUID statusId, String status, String statusCategory,
        UserRef reporter, UserRef assignee,
        UUID parentId, BigDecimal storyPoints, LocalDate dueDate,
        Map<String, Object> customFields,
        long version, Instant createdAt, Instant updatedAt) {
    public record UserRef(UUID id, String displayName, String avatarUrl) {
    }
}
