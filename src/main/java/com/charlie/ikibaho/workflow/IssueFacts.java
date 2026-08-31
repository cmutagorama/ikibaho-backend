package com.charlie.ikibaho.workflow;

import java.util.Map;
import java.util.UUID;

public record IssueFacts(
        UUID issueId,
        UUID projectId,
        UUID reporterId,
        UUID assigneeId,
        int openSubtaskCount,
        Map<String, Object> customFields) {
}
