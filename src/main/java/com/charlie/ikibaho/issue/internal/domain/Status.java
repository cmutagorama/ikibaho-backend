package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "status")
public class Status extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCategory category;

    @Column(nullable = false)
    private int position;

    protected Status() {
    }

    public Status(UUID organizationId, String name, StatusCategory category, int position) {
        this.organizationId = organizationId;
        this.name = name;
        this.category = category;
        this.position = position;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public StatusCategory getCategory() {
        return category;
    }

    public int getPosition() {
        return position;
    }
}
