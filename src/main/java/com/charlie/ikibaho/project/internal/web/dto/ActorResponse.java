package com.charlie.ikibaho.project.internal.web.dto;

import java.util.UUID;

public record ActorResponse(
        UUID id, String type,
        UUID userId,
        String displayName,
        String email,
        String avatarUrl,
        UUID groupId,
        String groupName) {
}
