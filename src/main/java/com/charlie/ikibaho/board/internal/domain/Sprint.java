package com.charlie.ikibaho.board.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A time-boxed batch of work.
 *
 * The state transitions live on the entity rather than in the service, because
 * they are the sprint's own rules: no caller should be able to complete a sprint
 * that never started, however it reached the database.
 */
@Entity
@Table(name = "sprint")
public class Sprint extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SprintState state;

    /** Planned dates, set at creation; the actual start/end are recorded separately. */
    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Sprint() {
    }

    public Sprint(UUID projectId, String name, String goal,
                  Instant plannedStart, Instant plannedEnd) {
        if (plannedStart != null && plannedEnd != null && !plannedEnd.isAfter(plannedStart)) {
            throw new ValidationException("A sprint must end after it starts");
        }
        this.projectId = projectId;
        this.name = name;
        this.goal = goal;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.state = SprintState.FUTURE;
    }

    public void start(Instant at) {
        if (state != SprintState.FUTURE) {
            throw new ConflictException("Only a future sprint can be started; " + name
                    + " is " + state.name().toLowerCase());
        }
        this.state = SprintState.ACTIVE;
        this.startedAt = at;
    }

    public void complete(Instant at) {
        if (state != SprintState.ACTIVE) {
            throw new ConflictException("Only an active sprint can be completed; " + name
                    + " is " + state.name().toLowerCase());
        }
        this.state = SprintState.COMPLETED;
        this.completedAt = at;
    }

    public void rename(String name, String goal) {
        if (state == SprintState.COMPLETED) {
            throw new ConflictException("A completed sprint cannot be edited");
        }
        this.name = name;
        this.goal = goal;
    }

    /** Scope may only change while the sprint can still be worked on. */
    public void requireOpen() {
        if (state == SprintState.COMPLETED) {
            throw new ConflictException("Sprint " + name + " is complete; its scope is fixed");
        }
    }

    public boolean isActive() {
        return state == SprintState.ACTIVE;
    }

    public boolean isCompleted() {
        return state == SprintState.COMPLETED;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getGoal() {
        return goal;
    }

    public SprintState getState() {
        return state;
    }

    public Instant getPlannedStart() {
        return plannedStart;
    }

    public Instant getPlannedEnd() {
        return plannedEnd;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
