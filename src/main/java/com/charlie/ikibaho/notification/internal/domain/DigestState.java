package com.charlie.ikibaho.notification.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Where each person's last digest got to.
 * <p>
 * Not derived from a fixed window: a job that emails "everything from the last
 * hour" double-sends when it runs late and drops things when it runs early. A
 * high-water mark is exact regardless of when the job actually fires.
 * <p>
 * Not a BaseEntity -- the user id is the primary key, because there is at most
 * one row per person and a surrogate id would only add a uniqueness constraint
 * to enforce that.
 */
@Entity
@Table(name = "digest_state")
public class DigestState {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DigestState() {
    }

    public DigestState(UUID userId, Instant lastSentAt) {
        this.userId = userId;
        this.lastSentAt = lastSentAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markSent(Instant at) {
        this.lastSentAt = at;
        this.updatedAt = at;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }
}
