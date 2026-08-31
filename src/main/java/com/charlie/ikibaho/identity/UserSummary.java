package com.charlie.ikibaho.identity;

import java.util.UUID;

/**
 * A person, not a person-in-a-workspace.
 *
 * organizationId used to live here, which made sense when an account belonged to
 * exactly one. It does not any more -- and the single consumer that read it
 * wanted the <em>session's</em> organization rather than the user's.
 */
public record UserSummary(UUID id, String email, String displayName, String avatarUrl) {
}
