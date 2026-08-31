package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.UserService.OrganizationMembership;

import java.util.List;
import java.util.UUID;

/**
 * Either signed in, or authenticated and awaiting a workspace choice.
 *
 * Modelled as one shape with a nullable token rather than an exception, because
 * "you belong to three workspaces" is a normal outcome, not an error. The
 * membership list is always present so a client can render a switcher without a
 * second call.
 */
public record LoginResult(UUID userId, TokenPair tokens,
                          List<OrganizationMembership> organizations) {

    static LoginResult signedIn(UUID userId, TokenPair tokens,
                                List<OrganizationMembership> organizations) {
        return new LoginResult(userId, tokens, organizations);
    }

    static LoginResult chooseWorkspace(UUID userId, List<OrganizationMembership> organizations) {
        return new LoginResult(userId, null, organizations);
    }

    public boolean needsWorkspaceChoice() {
        return tokens == null;
    }
}
