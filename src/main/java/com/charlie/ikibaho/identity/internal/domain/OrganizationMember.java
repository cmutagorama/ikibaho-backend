package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import com.charlie.ikibaho.platform.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One person's place in one organization.
 *
 * This is the join that makes an account global: a user row is the person, and a
 * membership row is their access to a workspace. One email, many workspaces --
 * what people expect from a Google identity, and what the previous per-org user
 * rows made impossible.
 */
@Entity
@Table(name = "organization_member")
public class OrganizationMember extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    protected OrganizationMember() {
    }

    private OrganizationMember(UUID organizationId, UUID userId, MemberRole role,
                               MembershipStatus status) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.status = status;
    }

    /** The founder of a new organization: admin, and active immediately. */
    public static OrganizationMember founder(UUID organizationId, UUID userId) {
        return new OrganizationMember(organizationId, userId, MemberRole.ADMIN,
                MembershipStatus.ACTIVE);
    }

    /** Invited but not yet accepted. Grants nothing until activated. */
    public static OrganizationMember invited(UUID organizationId, UUID userId) {
        return new OrganizationMember(organizationId, userId, MemberRole.MEMBER,
                MembershipStatus.INVITED);
    }

    public void activate() {
        if (status == MembershipStatus.DEACTIVATED) {
            throw new ConflictException("That membership was revoked; ask for a new invitation");
        }
        this.status = MembershipStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = MembershipStatus.DEACTIVATED;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }
}
