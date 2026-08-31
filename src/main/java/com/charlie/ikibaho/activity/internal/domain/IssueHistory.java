package com.charlie.ikibaho.activity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line of an issue's history.
 *
 * Written only by listeners reacting to events, never by a user-facing call --
 * which is what makes it trustworthy as an audit record.
 *
 * Note that {@code occurredAt} is not {@code createdAt}. The former is when the
 * thing happened in the domain, carried on the event; the latter is when this
 * row was written. They are normally milliseconds apart and can be minutes apart
 * if a failed listener is retried, so history is ordered by {@code occurredAt}.
 */
@Entity
@Table(name = "issue_history")
public class IssueHistory extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID issueId;

    @Column(nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private String issueKey;

    /** Null for anything the system did without a user asking. */
    @Column(updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private HistoryKind kind;

    /** The changed field, where the kind implies one ("status", "assignee"). */
    @Column(updatable = false)
    private String field;

    @Column(updatable = false)
    private String oldValue;

    @Column(updatable = false)
    private String newValue;

    /** The comment this line refers to, for kinds that point at another row. */
    @Column(updatable = false)
    private UUID referenceId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * Derived from the event's own identity, with a unique index behind it.
     *
     * The publication registry is at-least-once: a listener that throws, or an
     * app killed mid-handler, gets the same event again on restart. For an audit
     * log a duplicated line is a real defect -- it would read as the user having
     * done the thing twice -- so the database refuses the second insert rather
     * than the recorder trying to guess whether it already ran.
     */
    @Column(nullable = false, updatable = false, unique = true)
    private String dedupeKey;

    protected IssueHistory() {
    }

    public IssueHistory(UUID issueId, UUID projectId, String issueKey, UUID actorId,
                        HistoryKind kind, String field, String oldValue, String newValue,
                        UUID referenceId, Instant occurredAt, String dedupeKey) {
        this.issueId = issueId;
        this.projectId = projectId;
        this.issueKey = issueKey;
        this.actorId = actorId;
        this.kind = kind;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.referenceId = referenceId;
        this.occurredAt = occurredAt;
        this.dedupeKey = dedupeKey;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public UUID getActorId() {
        return actorId;
    }

    public HistoryKind getKind() {
        return kind;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
