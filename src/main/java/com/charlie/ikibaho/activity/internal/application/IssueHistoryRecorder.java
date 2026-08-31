package com.charlie.ikibaho.activity.internal.application;

import com.charlie.ikibaho.activity.internal.domain.HistoryKind;
import com.charlie.ikibaho.activity.internal.domain.IssueHistory;
import com.charlie.ikibaho.activity.internal.persistence.IssueHistoryRepository;
import com.charlie.ikibaho.issue.events.IssueAssigned;
import com.charlie.ikibaho.issue.events.IssueCommented;
import com.charlie.ikibaho.issue.events.IssueCreated;
import com.charlie.ikibaho.issue.events.IssueTransitioned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Turns issue events into history lines.
 *
 * {@code @ApplicationModuleListener} means: after the publishing transaction
 * commits, on another thread, in a transaction of its own, with the publication
 * recorded in the registry until this method returns normally. So a failure here
 * cannot roll back the user's edit, and cannot silently lose the history line
 * either -- the incomplete publication survives a restart and is retried.
 *
 * Every handler is therefore written to be safely repeatable.
 */
@Component
class IssueHistoryRecorder {
    private static final Logger log = LoggerFactory.getLogger(IssueHistoryRecorder.class);

    private final IssueHistoryRepository history;

    IssueHistoryRecorder(IssueHistoryRepository history) {
        this.history = history;
    }

    @ApplicationModuleListener
    void on(IssueCreated event) {
        record(new IssueHistory(event.issueId(), event.projectId(), event.issueKey(),
                event.reporterId(), HistoryKind.CREATED, null, null, event.summary(),
                null, event.occurredAt(),
                key(HistoryKind.CREATED, event.issueId(), event.occurredAt())));
    }

    @ApplicationModuleListener
    void on(IssueTransitioned event) {
        record(new IssueHistory(event.issueId(), event.projectId(), event.issueKey(),
                event.actorId(), HistoryKind.TRANSITIONED, "status",
                event.fromStatus(), event.toStatus(), event.transitionId(),
                event.occurredAt(),
                key(HistoryKind.TRANSITIONED, event.issueId(), event.occurredAt())));
    }

    @ApplicationModuleListener
    void on(IssueAssigned event) {
        record(new IssueHistory(event.issueId(), event.projectId(), event.issueKey(),
                event.actorId(), HistoryKind.ASSIGNED, "assignee",
                asText(event.previousAssigneeId()), asText(event.assigneeId()),
                event.assigneeId(), event.occurredAt(),
                key(HistoryKind.ASSIGNED, event.issueId(), event.occurredAt())));
    }

    @ApplicationModuleListener
    void on(IssueCommented event) {
        // A comment has its own id, so the dedupe key can be exact rather than
        // leaning on the timestamp.
        record(new IssueHistory(event.issueId(), event.projectId(), event.issueKey(),
                event.authorId(), HistoryKind.COMMENTED, null, null, event.excerpt(),
                event.commentId(), event.occurredAt(),
                HistoryKind.COMMENTED + ":" + event.commentId()));
    }

    /**
     * Insert, unless this exact event has already been recorded.
     *
     * The check-then-insert is racy on its own, so the unique index is the real
     * guard and this is only an early out. Losing the race surfaces as a
     * constraint violation, which means the line is already there -- the outcome
     * we wanted. Swallowing it lets the publication complete instead of retrying
     * forever on an event that has, in fact, been handled.
     */
    private void record(IssueHistory line) {
        if (history.existsByDedupeKey(line.getDedupeKey())) {
            log.debug("Skipping already-recorded history line {}", line.getDedupeKey());
            return;
        }
        try {
            history.save(line);
        } catch (DataIntegrityViolationException e) {
            log.debug("History line {} was recorded concurrently", line.getDedupeKey());
        }
    }

    private static String key(HistoryKind kind, UUID issueId, Instant occurredAt) {
        return kind + ":" + issueId + ":" + occurredAt.toEpochMilli();
    }

    private static String asText(UUID value) {
        return value == null ? null : value.toString();
    }
}
