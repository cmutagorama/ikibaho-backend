package com.charlie.ikibaho.integration.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt-tracked delivery.
 * <p>
 * The payload is frozen into this row at enqueue time rather than rebuilt at
 * send time. A retry three minutes later must deliver what was true when the
 * event happened -- not the issue's current state, which may have moved on twice
 * since.
 */
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery extends BaseEntity {
    /**
     * Roughly 30 minutes of backoff before giving up.
     */
    private static final int MAX_ATTEMPTS = 6;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(20);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    @Column(name = "webhook_id", nullable = false, updatable = false)
    private UUID webhookId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID webhookId, UUID organizationId, String eventType, String payload, UUID eventId) {
        this.webhookId = webhookId;
        this.organizationId = organizationId;
        this.eventType = eventType;
        this.eventId = eventId;
        this.payload = payload;
        this.state = State.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
    }

    /**
     * Errors from a remote server are unbounded; the column is not.
     */
    private static String truncate(String error) {
        if (error == null) return null;
        return error.length() <= 500 ? error : error.substring(0, 500);
    }

    public void succeeded(int status, Instant at) {
        this.state = State.DELIVERED;
        this.attempts++;
        this.lastStatus = status;
        this.lastError = null;
        this.deliveredAt = at;
    }

    /**
     * Record a failure and schedule the retry.
     * <p>
     * Exponential backoff, capped: a receiver that is down comes back at some
     * point, and hammering it every ten seconds until then helps nobody. The cap
     * stops the interval growing past the point of usefulness.
     */
    public void failed(Integer status, String error, Instant now) {
        this.attempts++;
        this.lastStatus = status;
        this.lastError = truncate(error);

        if (attempts >= MAX_ATTEMPTS) {
            this.state = State.FAILED;
            return;
        }
        long backoffSeconds = Math.min(
                BASE_BACKOFF.toSeconds() * (1L << (attempts - 1)),
                MAX_BACKOFF.toSeconds());
        this.nextAttemptAt = now.plusSeconds(backoffSeconds);
    }

    /**
     * Puts an abandoned delivery back in the queue, attempts reset.
     */
    public void retryNow() {
        this.state = State.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
        this.lastError = null;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public State getState() {
        return state;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastStatus() {
        return lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public enum State {PENDING, DELIVERED, FAILED}
}
