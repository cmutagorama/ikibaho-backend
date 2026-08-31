package com.charlie.ikibaho.project.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import java.util.UUID;

@Entity
@Table(name = "project")
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)
public class Project extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "permission_scheme_id", nullable = false)
    private UUID permissionSchemeId;

    @Column(name = "issue_counter", nullable = false)
    private long issueCounter;

    @Column(name = "workflow_id")
    private UUID workflowId;

    protected Project() {
    }

    public Project(UUID organizationId, String key, String name, UUID leadId, UUID permissionSchemeId) {
        this.organizationId = organizationId;
        this.key = key;
        this.name = name;
        this.leadId = leadId;
        this.permissionSchemeId = permissionSchemeId;
        this.issueCounter = 0;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeLead(UUID leadId) {
        this.leadId = leadId;
    }

    public void changeScheme(UUID schemeId) {
        this.permissionSchemeId = schemeId;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void assignWorkflow(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getPermissionSchemeId() {
        return permissionSchemeId;
    }

    public long getIssueCounter() {
        return issueCounter;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }
}
