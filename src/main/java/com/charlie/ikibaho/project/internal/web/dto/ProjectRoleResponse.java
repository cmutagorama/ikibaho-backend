package com.charlie.ikibaho.project.internal.web.dto;

import java.util.List;
import java.util.UUID;

public record ProjectRoleResponse(
        UUID id, String name, String description,
        List<ActorResponse> actors) {
}
