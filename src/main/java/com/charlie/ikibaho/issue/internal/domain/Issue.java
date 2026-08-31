package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "issue")
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)
public class Issue extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "issue_key", nullable = false, updatable = false)
    private String issueKey;

    @Column(name = "issue_number", nullable = false, updatable = false)
    private long issueNumber;

    @Column(name = "type_id", nullable = false)
    private UUID typeId;

    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(nullable = false)
    private String summary;

    private String description;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "rank", nullable = false)
    private String rank;    // LexoRank; assigned at creation, reordered by drag

    @Column(name = "story_points")
    private BigDecimal storyPoints;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * User-defined fields. Core fields stay real columns; only these live in jsonb.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false)
    private Map<String, Object> customFields = new HashMap<>();

    /**
     * Two people editing the same issue is the most common real conflict here.
     * Without this, the second write silently overwrites the first.
     */
    @Version
    private long version;

    protected Issue() {
    }

    public Issue(UUID organizationId, UUID projectId, String issueKey, long issueNumber,
                 UUID typeId, UUID statusId, String summary, UUID reporterId) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.issueKey = issueKey;
        this.issueNumber = issueNumber;
        this.typeId = typeId;
        this.statusId = statusId;
        this.summary = summary;
        this.reporterId = reporterId;
        this.priority = Priority.MEDIUM;
    }

    public void changeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Summary must not be blank");
        }
        this.summary = summary;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changePriority(Priority priority) {
        this.priority = priority;
    }

    public void changeStatus(UUID statusId) {
        this.statusId = statusId;
    }

    public void changeType(UUID typeId) {
        this.typeId = typeId;
    }

    public void assignTo(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public void unassign() {
        this.assigneeId = null;
    }

    public void changeStoryPoints(BigDecimal points) {
        this.storyPoints = points;
    }

    public void changeDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public void changeRank(String rank) {
        this.rank = rank;
    }

    public void changeParent(UUID parentId) {
        if (parentId != null && parentId.equals(getId())) {
            throw new IllegalArgumentException("An issue cannot be its own parent");
        }
        this.parentId = parentId;
    }

    public void setCustomField(String key, Object value) {
        if (value == null) {
            customFields.remove(key);
        } else {
            customFields.put(key, value);
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public long getIssueNumber() {
        return issueNumber;
    }

    public UUID getTypeId() {
        return typeId;
    }

    public UUID getStatusId() {
        return statusId;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getRank() {
        return rank;
    }

    public BigDecimal getStoryPoints() {
        return storyPoints;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public long getVersion() {
        return version;
    }

    public Map<String, Object> getCustomFields() {
        return Map.copyOf(customFields);
    }
}
