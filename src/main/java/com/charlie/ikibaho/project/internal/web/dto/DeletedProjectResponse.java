package com.charlie.ikibaho.project.internal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record DeletedProjectResponse(UUID id, String key, String name, Instant deletedAt) {
}
