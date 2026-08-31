package com.charlie.ikibaho.issue;

import java.util.UUID;

public record IssueSummary(UUID id, UUID projectId, String issueKey, String summary,
                           UUID statusId, UUID assigneeId, UUID reporterId) {
}
