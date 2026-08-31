package com.charlie.ikibaho.issue.internal.web.dto;

import com.charlie.ikibaho.issue.internal.domain.Priority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Null means "leave unchanged". version is mandatory -- it is the optimistic
 * lock token the client read with the issue.
 */

public record UpdateIssueCommand(
        long version,
        String summary,
        String description,
        Priority priority,
        UUID typeId,
        BigDecimal storyPoints,
        LocalDate dueDate,
        Map<String, Object> customFields) {
}
