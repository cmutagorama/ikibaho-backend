package com.charlie.ikibaho.identity.internal.web.dto;

import com.charlie.ikibaho.identity.UserService.OrganizationMembership;

import java.util.List;
import java.util.UUID;

/**
 * The result of authenticating.
 *
 * {@code tokens} is null when the account belongs to several workspaces and none
 * was named -- not an error, just an unfinished choice. {@code organizations} is
 * always populated, so a client can render the switcher without a second call.
 */
public record LoginResponse(UUID userId, TokenResponse tokens,
                            List<OrganizationMembership> organizations) {
}
