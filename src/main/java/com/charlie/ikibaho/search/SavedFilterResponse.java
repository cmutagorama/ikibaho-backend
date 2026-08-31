package com.charlie.ikibaho.search;

import java.util.UUID;

public record SavedFilterResponse(
        UUID id,
        String name,
        String jql,
        String description,
        boolean shared,
        UUID ownerId,
        boolean ownedByMe
) {
}
