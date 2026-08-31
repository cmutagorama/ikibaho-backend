package com.charlie.ikibaho.issue.internal.web.dto;

import com.charlie.ikibaho.issue.internal.domain.Priority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateIssueCommand(
        UUID typeId,
        String summary,
        String description,
        Priority priority,
        UUID assigneeId,
        UUID parentId,
        BigDecimal storyPoints,
        LocalDate dueDate,
        Map<String, Object> customFields) {
}
