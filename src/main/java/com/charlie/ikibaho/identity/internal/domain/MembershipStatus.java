package com.charlie.ikibaho.identity.internal.domain;

/**
 * Membership state, deliberately distinct from the account's own status.
 *
 * Removing someone from one workspace must not touch their access to another,
 * and an account can be perfectly healthy while one of its memberships is
 * revoked. {@link UserStatus} still exists and answers a different question:
 * whether the account can authenticate at all.
 */
public enum MembershipStatus {
    INVITED,
    ACTIVE,
    DEACTIVATED
}
