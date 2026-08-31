package com.charlie.ikibaho.project;

import java.util.UUID;

public record IssueContext(UUID issueId, UUID projectId, UUID reporterId, UUID assigneeId) {
}
