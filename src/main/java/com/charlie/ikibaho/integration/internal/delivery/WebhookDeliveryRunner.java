package com.charlie.ikibaho.integration.internal.delivery;

import com.charlie.ikibaho.integration.internal.domain.Webhook;
import com.charlie.ikibaho.integration.internal.domain.WebhookDelivery;
import com.charlie.ikibaho.integration.internal.persistence.WebhookDeliveryRepository;
import com.charlie.ikibaho.integration.internal.persistence.WebhookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional half of webhook delivery.
 * <p>
 * Split out of {@link WebhookDeliveryJob} because Spring's {@code @Transactional}
 * works through a proxy: a method calling another on {@code this} goes straight
 * to the target and the annotation is silently ignored. The job used to call its
 * own transactional methods, so the claim ran outside a transaction and the
 * result of each delivery was never flushed -- every send appeared to happen and
 * every row stayed PENDING.
 *
 * <p>Both methods are public for the same reason: Spring skips proxying
 * non-public methods, which would reintroduce the bug quietly.
 */
@Component
public class WebhookDeliveryRunner {

    private final WebhookDeliveryRepository deliveries;
    private final WebhookRepository webhooks;
    private final WebhookSender sender;

    WebhookDeliveryRunner(WebhookDeliveryRepository deliveries, WebhookRepository webhooks,
                          WebhookSender sender) {
        this.deliveries = deliveries;
        this.webhooks = webhooks;
        this.sender = sender;
    }

    /**
     * Ids only, not entities.
     * <p>
     * Each delivery is then loaded inside its own transaction. Carrying entities
     * across the boundary would hand the next transaction detached objects whose
     * changes go nowhere -- the same class of bug this split exists to fix.
     */
    @Transactional
    public List<UUID> claimDue(int batchSize) {
        return deliveries.claimDue(Instant.now(), PageRequest.ofSize(batchSize)).stream()
                .map(WebhookDelivery::getId)
                .toList();
    }

    /**
     * One delivery, one transaction.
     * <p>
     * REQUIRES_NEW so a failure cannot poison the rest of the batch, and so the
     * attempt count is committed even when the send fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverOne(UUID deliveryId) {
        Optional<WebhookDelivery> found = deliveries.findById(deliveryId);
        if (found.isEmpty()) {
            return;
        }
        WebhookDelivery delivery = found.get();

        Optional<Webhook> webhook = webhooks.findById(delivery.getWebhookId());
        if (webhook.isEmpty() || !webhook.get().isActive()) {
            // Deleted or paused while queued. Not a failure -- there is simply
            // nobody left who asked for this.
            delivery.failed(null, "Webhook is no longer active", Instant.now());
            return;
        }

        WebhookSender.Result result = sender.send(
                webhook.get().getUrl(),
                webhook.get().getSecret(),
                delivery.getEventType(),
                // The event id, not the row id: it is what the payload carries, and
                // a receiver deduplicating on the header must be able to match it
                // against the body.
                delivery.getEventId().toString(),
                delivery.getPayload());

        Instant now = Instant.now();
        if (result.success()) {
            delivery.succeeded(result.status(), now);
        } else {
            delivery.failed(result.status(), result.error(), now);
        }
    }
}
