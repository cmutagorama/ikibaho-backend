package com.charlie.ikibaho.notification.internal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String kind,
        String title,
        String body,
        UUID issueId,
        String issueKey,
        UUID projectId,
        Instant readAt,
        Instant occurredAt
) {
}
