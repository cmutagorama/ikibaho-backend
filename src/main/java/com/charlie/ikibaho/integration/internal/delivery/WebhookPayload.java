package com.charlie.ikibaho.integration.internal.delivery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The JSON envelope every webhook receives.
 * <p>
 * Stable by contract: {@code id}, {@code event}, {@code occurredAt}, {@code data}.
 * Receivers switch on {@code event} and read {@code data}, so new event types can
 * be added without breaking anyone parsing the outer shape.
 * <p>
 * {@code id} is the delivery id, and it is what a receiver should deduplicate on
 * -- retries reuse it, so seeing the same id twice means the same event, not a
 * second one.
 */
record WebhookPayload(
        UUID id,
        String event,
        Instant occurredAt,
        Map<String, Object> data
) {
}
