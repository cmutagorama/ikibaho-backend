package com.charlie.ikibaho.workflow.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "workflow")
public class Workflow extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "initial_status_id", nullable = false)
    private UUID initialStatusId;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWorkflow;      // never name a boolean field "isX" -- derived-query trap

    protected Workflow() {
    }

    public Workflow(UUID organizationId, String name, UUID initialStatusId, boolean defaultWorkflow) {
        this.organizationId = organizationId;
        this.name = name;
        this.initialStatusId = initialStatusId;
        this.defaultWorkflow = defaultWorkflow;
    }

    public void changeInitialStatus(UUID statusId) {
        this.initialStatusId = statusId;
    }

    public void rename(String name) {
        this.name = name;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public UUID getInitialStatusId() {
        return initialStatusId;
    }

    public boolean isDefaultWorkflow() {
        return defaultWorkflow;
    }
}
