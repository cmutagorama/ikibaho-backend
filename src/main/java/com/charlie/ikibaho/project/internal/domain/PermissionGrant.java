package com.charlie.ikibaho.project.internal.domain;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "permission_grant")
public class PermissionGrant extends BaseEntity {
    @Column(name = "scheme_id", nullable = false)
    private UUID schemeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", nullable = false)
    private HolderType holderType;

    @Column(name = "holder_ref")
    private UUID holderRef;

    protected PermissionGrant() {
    }

    public PermissionGrant(UUID schemeId, Permission permission, HolderType holderType, UUID holderRef) {
        if (holderType.requiresReference() && holderRef == null) {
            throw new IllegalArgumentException(holderType + " requires a holder reference");
        }

        if (holderType.isDynamic() && holderRef != null) {
            throw new IllegalArgumentException(holderType + " must not have a holder reference");
        }
        this.schemeId = schemeId;
        this.permission = permission;
        this.holderType = holderType;
        this.holderRef = holderRef;
    }

    public UUID getSchemeId() {
        return schemeId;
    }

    public Permission getPermission() {
        return permission;
    }

    public HolderType getHolderType() {
        return holderType;
    }

    public UUID getHolderRef() {
        return holderRef;
    }
}
