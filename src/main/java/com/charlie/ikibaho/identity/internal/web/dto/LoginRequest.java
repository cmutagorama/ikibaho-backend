package com.charlie.ikibaho.identity.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param organization the workspace slug. Optional: omit it when the account
 *                     belongs to exactly one, and the server resolves it. With
 *                     several, the server replies with the list instead of a
 *                     token and the client re-posts with a choice.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password,
                           String organization) {
}
