package com.charlie.ikibaho.issue.internal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        UUID issueId,
        String filename,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant uploadedAt
) {
}
