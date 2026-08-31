package com.charlie.ikibaho.notification.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One in-app notification for one person.
 *
 * The text is rendered at write time rather than at read time. A notification is
 * a record of what someone was told, so it should keep saying that even after
 * the issue moves on -- "Dara assigned IKB-14 to you" stays true regardless of
 * who holds the issue now.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationKind kind;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(updatable = false)
    private String body;

    @Column(updatable = false)
    private UUID issueId;

    @Column(updatable = false)
    private String issueKey;

    @Column(updatable = false)
    private UUID projectId;

    /** Null until read; the timestamp is more useful than a boolean. */
    private Instant readAt;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    /** Same at-least-once guard as issue_history; see IssueHistory#dedupeKey. */
    @Column(nullable = false, updatable = false, unique = true)
    private String dedupeKey;

    protected Notification() {
    }

    public Notification(UUID recipientId, NotificationKind kind, String title, String body,
                        UUID issueId, String issueKey, UUID projectId, Instant occurredAt,
                        String dedupeKey) {
        this.recipientId = recipientId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.projectId = projectId;
        this.occurredAt = occurredAt;
        this.dedupeKey = dedupeKey;
    }

    /** Idempotent: marking an already-read notification read again changes nothing. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public boolean isReadBy(UUID userId) {
        return recipientId.equals(userId) && readAt != null;
    }

    public boolean belongsTo(UUID userId) {
        return recipientId.equals(userId);
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
