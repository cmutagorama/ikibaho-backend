package com.charlie.ikibaho.issue.internal.web.dto;

import com.charlie.ikibaho.issue.internal.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateIssueRequest(
        @NotNull UUID typeId,
        @NotBlank @Size(max = 500) String summary,
        String description,
        Priority priority,
        UUID assigneeId,
        UUID parentId,
        BigDecimal storyPoints,
        LocalDate dueDate,
        Map<String, Object> customFields
) {
}
