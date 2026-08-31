package com.charlie.ikibaho.project.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "permission_scheme")
public class PermissionScheme extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultScheme;

    protected PermissionScheme() {}

    public PermissionScheme(UUID organizationId, String name, boolean isDefaultScheme) {
        this.organizationId = organizationId;
        this.name = name;
        this.defaultScheme = isDefaultScheme;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public boolean isDefaultScheme() {
        return defaultScheme;
    }
}
