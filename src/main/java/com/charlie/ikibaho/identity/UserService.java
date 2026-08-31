package com.charlie.ikibaho.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {
    Optional<UserSummary> findById(UUID id);

    /**
     * Batch lookup -- use this instead of calling findById in a loop.
     */
    List<UserSummary> findAllById(Collection<UUID> ids);

    boolean groupExistsInOrganization(UUID groupId, UUID organizationId);

    /**
     * True only for an ACTIVE membership.
     *
     * Invited-but-unaccepted and revoked both count as absent, so an invitation
     * grants nothing until it is accepted and revocation takes effect at once.
     */
    boolean userExistsInOrganization(UUID userId, UUID organizationId);

    /** The workspaces this account may enter. */
    List<OrganizationMembership> membershipsOf(UUID userId);

    /** Empty when the account has no active membership there. */
    Optional<OrganizationMembership> membership(UUID userId, UUID organizationId);

    record OrganizationMembership(UUID organizationId, String slug, String name, String role) {
    }
}
