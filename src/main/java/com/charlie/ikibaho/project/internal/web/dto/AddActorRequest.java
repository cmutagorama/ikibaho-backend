package com.charlie.ikibaho.project.internal.web.dto;

import java.util.UUID;

public record AddActorRequest(UUID userId, UUID groupId) {
}
