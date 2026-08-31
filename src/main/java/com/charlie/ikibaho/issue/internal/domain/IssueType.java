package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "issue_type")
public class IssueType extends BaseEntity {
    public static final int LEVEL_EPIC = 1;
    public static final int LEVEL_STANDARD = 0;
    public static final int LEVEL_SUBTASK = -1;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(name = "hierarchy_level", nullable = false)
    private int hierarchyLevel;

    protected IssueType() {
    }

    public IssueType(UUID organizationId, String name, int hierarchyLevel) {
        this.organizationId = organizationId;
        this.name = name;
        this.hierarchyLevel = hierarchyLevel;
    }

    public boolean isEpic() {
        return hierarchyLevel == LEVEL_EPIC;
    }

    public boolean isSubtask() {
        return hierarchyLevel == LEVEL_SUBTASK;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }
}
