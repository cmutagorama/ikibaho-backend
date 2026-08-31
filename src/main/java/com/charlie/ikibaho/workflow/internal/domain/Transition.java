package com.charlie.ikibaho.workflow.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "transition")
public class Transition extends BaseEntity {
    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private String name;

    /**
     * NULL means global: reachable from any status in the workflow.
     */
    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    protected Transition() {
    }

    public Transition(UUID workflowId, String name, UUID fromStatusId, UUID toStatusId) {
        if (toStatusId.equals(fromStatusId)) {
            throw new IllegalArgumentException("A transition cannot target its own source status");
        }
        this.workflowId = workflowId;
        this.name = name;
        this.fromStatusId = fromStatusId;
        this.toStatusId = toStatusId;
    }

    public boolean isGlobal() {
        return fromStatusId == null;
    }

    /**
     * Reachability from a given status. Both callers -- listing and execution --
     * must use this, or they drift: a global transition is reachable from anywhere,
     * including from its own destination, which would make executing it a silent no-op.
     */
    public boolean isAvailableFrom(UUID statusId) {
        if (toStatusId.equals(statusId)) {
            return false;                       // already there
        }
        return isGlobal() || fromStatusId.equals(statusId);
    }

    public boolean targets(UUID statusId) {
        return toStatusId.equals(statusId);
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getName() {
        return name;
    }

    public UUID getFromStatusId() {
        return fromStatusId;
    }

    public UUID getToStatusId() {
        return toStatusId;
    }
}
