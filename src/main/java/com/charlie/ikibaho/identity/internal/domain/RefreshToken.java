package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * The workspace this session belongs to.
     *
     * Refresh is per-workspace, so revoking somebody's access to one organization
     * cannot be worked around by refreshing an older token, and a refresh always
     * returns to the workspace they actually signed in to.
     */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() { }

    public RefreshToken(UUID userId, UUID organizationId, UUID familyId,
                        String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isSpent()             { return usedAt != null || revokedAt != null; }
    public boolean isExpired(Instant at) { return expiresAt.isBefore(at); }
    public void markUsed(Instant at)     { this.usedAt = at; }
    public void revoke(Instant at)       { this.revokedAt = at; }

    public UUID getUserId()   { return userId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getFamilyId() { return familyId; }
}
