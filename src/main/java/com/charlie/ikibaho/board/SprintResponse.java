package com.charlie.ikibaho.board;

import java.time.Instant;
import java.util.UUID;

public record SprintResponse(
        UUID id,
        UUID projectId,
        String name,
        String goal,
        String state,
        Instant plannedStart,
        Instant plannedEnd,
        Instant startedAt,
        Instant completedAt,
        int issueCount
) {
}
