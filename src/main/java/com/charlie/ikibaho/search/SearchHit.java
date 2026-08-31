package com.charlie.ikibaho.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchHit(
        UUID issueId,
        UUID projectId,
        String projectKey,
        String issueKey,
        String summary,
        String typeName,
        String statusName,
        String statusCategory,
        String priority,
        UUID assigneeId,
        UUID reporterId,
        BigDecimal storyPoints,
        Instant createdAt,
        Instant updatedAt) {
}
