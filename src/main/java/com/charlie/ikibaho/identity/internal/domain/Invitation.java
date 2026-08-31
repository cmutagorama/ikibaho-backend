package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation")
public class Invitation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    /**
     * SHA-256 of the token. The raw value exists only in the invitation link.
     */
    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Invitation() {
    }

    public Invitation(UUID organizationId, UUID userId, UUID invitedBy,
                      String tokenHash, Instant expiresAt) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.invitedBy = invitedBy;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isPending(Instant at) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(at);
    }

    public void accept(Instant at) {
        this.acceptedAt = at;
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
