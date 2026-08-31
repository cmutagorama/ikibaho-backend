package com.charlie.ikibaho.integration.internal.application;

import com.charlie.ikibaho.integration.WebhookCreated;
import com.charlie.ikibaho.integration.WebhookResponse;
import com.charlie.ikibaho.integration.internal.domain.Webhook;
import com.charlie.ikibaho.integration.internal.domain.WebhookDelivery;
import com.charlie.ikibaho.integration.internal.persistence.WebhookDeliveryRepository;
import com.charlie.ikibaho.integration.internal.persistence.WebhookRepository;
import com.charlie.ikibaho.integration.internal.security.HmacSigner;
import com.charlie.ikibaho.platform.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Webhook administration.
 * <p>
 * Org-scoped rather than project-scoped, and restricted to site admins by the
 * controller: a webhook receives events from every project in the organization,
 * so granting one is granting a read of everything.
 */
@Service
@Transactional(readOnly = true)
public class WebhookService {
    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final HmacSigner signer;

    WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveries,
                   HmacSigner signer) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.signer = signer;
    }

    @Transactional
    public WebhookCreated create(UUID organizationId, UUID actorId, String url,
                                 String description, Set<String> eventTypes) {
        String secret = signer.newSecret();
        Webhook webhook = webhooks.save(
                new Webhook(organizationId, url, secret, description, eventTypes, actorId));
        return new WebhookCreated(toResponse(webhook), secret);
    }

    public List<WebhookResponse> list(UUID organizationId) {
        return webhooks.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    public WebhookResponse get(UUID webhookId, UUID organizationId) {
        return toResponse(requireInOrg(webhookId, organizationId));
    }

    @Transactional
    public WebhookResponse update(UUID webhookId, UUID organizationId, String url,
                                  String description, Set<String> eventTypes, boolean active) {
        Webhook webhook = requireInOrg(webhookId, organizationId);
        webhook.update(url, description, eventTypes, active);
        return toResponse(webhook);
    }

    @Transactional
    public void delete(UUID webhookId, UUID organizationId) {
        webhooks.delete(requireInOrg(webhookId, organizationId));
    }

    /**
     * The delivery log, for working out why a receiver is not getting events.
     */
    public List<DeliveryView> recentDeliveries(UUID webhookId, UUID organizationId, int limit) {
        requireInOrg(webhookId, organizationId);
        return deliveries.findByWebhookIdOrderByCreatedAtDesc(webhookId, PageRequest.ofSize(limit))
                .stream()
                .map(d -> new DeliveryView(d.getId(), d.getEventType(), d.getState().name(),
                        d.getAttempts(), d.getLastStatus(), d.getLastError(),
                        d.getNextAttemptAt(), d.getDeliveredAt(), d.getCreatedAt()))
                .toList();
    }

    /**
     * Requeues a delivery that exhausted its attempts, once the endpoint is fixed.
     */
    @Transactional
    public void retry(UUID deliveryId, UUID organizationId) {
        WebhookDelivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery", deliveryId));
        if (!delivery.getOrganizationId().equals(organizationId)) {
            throw new NotFoundException("Delivery", deliveryId);
        }
        delivery.retryNow();
    }

    /**
     * 404 rather than 403 across organizations: whether another tenant has a
     * webhook with this id is not information to hand out.
     */
    private Webhook requireInOrg(UUID webhookId, UUID organizationId) {
        Webhook webhook = webhooks.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook", webhookId));
        if (!webhook.getOrganizationId().equals(organizationId)) {
            throw new NotFoundException("Webhook", webhookId);
        }
        return webhook;
    }

    private WebhookResponse toResponse(Webhook w) {
        return new WebhookResponse(w.getId(), w.getUrl(), w.getDescription(), w.getEventTypes(),
                w.isActive(), w.getCreatedAt(),
                deliveries.countByWebhookIdAndState(w.getId(), WebhookDelivery.State.PENDING),
                deliveries.countByWebhookIdAndState(w.getId(), WebhookDelivery.State.FAILED));
    }

    public record DeliveryView(
            UUID id,
            String eventType,
            String state,
            int attempts,
            Integer lastStatus,
            String lastError,
            Instant nextAttemptAt,
            Instant deliveredAt,
            Instant createdAt) {
    }
}
