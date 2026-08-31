package com.charlie.ikibaho.project;

import java.util.UUID;

public record ProjectSummary(UUID id, UUID organizationId, String key, String name, UUID leadId) {
}
