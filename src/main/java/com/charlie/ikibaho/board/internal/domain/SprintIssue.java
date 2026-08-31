package com.charlie.ikibaho.board.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Sprint membership, owned by the board module.
 *
 * Deliberately a join table rather than a {@code sprint_id} column on the issue
 * row: sprints are board's concept, and issue must not grow a column that only
 * board understands. It also keeps issue free of any reference to board, which
 * is what stops the two modules forming a cycle.
 */
@Entity
@Table(name = "sprint_issue")
public class SprintIssue {

    @EmbeddedId
    private Id id;

    protected SprintIssue() {
    }

    public SprintIssue(UUID sprintId, UUID issueId) {
        this.id = new Id(sprintId, issueId);
    }

    public UUID getSprintId() {
        return id.sprintId();
    }

    public UUID getIssueId() {
        return id.issueId();
    }

    /**
     * The pair is the key, so a double-add is a primary key violation rather than
     * a duplicate row -- membership is a set, and the database enforces it.
     */
    @Embeddable
    public record Id(
            @Column(name = "sprint_id", nullable = false) UUID sprintId,
            @Column(name = "issue_id", nullable = false) UUID issueId
    ) implements Serializable {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(sprintId, other.sprintId)
                    && Objects.equals(issueId, other.issueId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sprintId, issueId);
        }
    }
}
