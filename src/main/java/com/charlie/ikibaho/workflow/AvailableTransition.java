package com.charlie.ikibaho.workflow;

import java.util.UUID;

public record AvailableTransition(UUID id, String name, UUID toStatusId, String toStatusName) {
}
