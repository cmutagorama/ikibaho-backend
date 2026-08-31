package com.charlie.ikibaho.integration.internal.delivery;

import com.charlie.ikibaho.integration.internal.domain.Webhook;
import com.charlie.ikibaho.integration.internal.domain.WebhookDelivery;
import com.charlie.ikibaho.integration.internal.domain.WebhookEvent;
import com.charlie.ikibaho.integration.internal.persistence.WebhookDeliveryRepository;
import com.charlie.ikibaho.integration.internal.persistence.WebhookRepository;
import com.charlie.ikibaho.issue.events.*;
import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.project.events.ProjectCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into queued deliveries. Sends nothing itself.
 * <p>
 * Enqueuing is fast and local; sending is slow and involves a stranger's server.
 * Doing both here would let one unresponsive endpoint occupy an event-listener
 * thread for its full timeout, and would lose every pending retry on restart.
 * The rows are the queue, and {@link WebhookDeliveryJob} drains it.
 */
@Component
class WebhookDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final ProjectService projects;
    private final ObjectMapper json;

    WebhookDispatcher(WebhookRepository webhooks, WebhookDeliveryRepository deliveries,
                      ProjectService projects, ObjectMapper json) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.projects = projects;
        this.json = json;
    }

    @ApplicationModuleListener
    void on(IssueCreated event) {
        enqueue(WebhookEvent.ISSUE_CREATED, event.organizationId(), Map.of(
                "issueId", event.issueId(),
                "issueKey", event.issueKey(),
                "projectId", event.projectId(),
                "summary", event.summary(),
                "reporterId", event.reporterId()), event.occurredAt());
    }

    @ApplicationModuleListener
    void on(IssueUpdated event) {
        organizationOf(event.projectId()).ifPresent(orgId ->
                enqueue(WebhookEvent.ISSUE_UPDATED, orgId, Map.of(
                        "issueId", event.issueId(),
                        "issueKey", event.issueKey(),
                        "projectId", event.projectId(),
                        "actorId", event.actorId()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(IssueDeleted event) {
        organizationOf(event.projectId()).ifPresent(orgId ->
                enqueue(WebhookEvent.ISSUE_DELETED, orgId, Map.of(
                        "issueId", event.issueId(),
                        "issueKey", event.issueKey(),
                        "projectId", event.projectId(),
                        "actorId", event.actorId()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(IssueTransitioned event) {
        organizationOf(event.projectId()).ifPresent(orgId -> {
            Map<String, Object> data = new HashMap<>();
            data.put("issueId", event.issueId());
            data.put("issueKey", event.issueKey());
            data.put("projectId", event.projectId());
            data.put("fromStatus", event.fromStatus());
            data.put("toStatus", event.toStatus());
            data.put("actorId", event.actorId());
            enqueue(WebhookEvent.ISSUE_TRANSITIONED, orgId, data, event.occurredAt());
        });
    }

    @ApplicationModuleListener
    void on(IssueAssigned event) {
        organizationOf(event.projectId()).ifPresent(orgId -> {
            // HashMap, not Map.of: assigneeId is null on an unassignment, and
            // Map.of rejects nulls outright.
            Map<String, Object> data = new HashMap<>();
            data.put("issueId", event.issueId());
            data.put("issueKey", event.issueKey());
            data.put("projectId", event.projectId());
            data.put("previousAssigneeId", event.previousAssigneeId());
            data.put("assigneeId", event.assigneeId());
            data.put("actorId", event.actorId());
            enqueue(WebhookEvent.ISSUE_ASSIGNED, orgId, data, event.occurredAt());
        });
    }

    @ApplicationModuleListener
    void on(IssueCommented event) {
        organizationOf(event.projectId()).ifPresent(orgId ->
                enqueue(WebhookEvent.ISSUE_COMMENTED, orgId, Map.of(
                        "issueId", event.issueId(),
                        "issueKey", event.issueKey(),
                        "commentId", event.commentId(),
                        "authorId", event.authorId(),
                        "excerpt", event.excerpt()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(ProjectCreated event) {
        enqueue(WebhookEvent.PROJECT_CREATED, event.organizationId(), Map.of(
                "projectId", event.projectId(),
                "key", event.key(),
                "name", event.name(),
                "leadId", event.leadId()), event.occurredAt());
    }

    /**
     * One row per subscribed webhook.
     * <p>
     * The payload is serialized once here and stored, so a retry sends byte-identical
     * content -- which matters because the signature covers the exact bytes.
     */
    private void enqueue(String eventType, UUID organizationId,
                         Map<String, Object> data, Instant occurredAt) {
        List<Webhook> subscribers = webhooks.findByOrganizationIdAndActiveTrue(organizationId)
                .stream().filter(w -> w.isSubscribedTo(eventType)).toList();

        if (subscribers.isEmpty()) {
            return;
        }

        for (Webhook webhook : subscribers) {
            // Generated up front so the payload can carry it before the row exists.
            // This id -- not the row's -- is what receivers deduplicate on, because
            // a retry must present the same one.
            UUID eventId = UUID.randomUUID();
            try {
                String body = json.writeValueAsString(
                        new WebhookPayload(eventId, eventType, occurredAt, data));
                deliveries.save(new WebhookDelivery(
                        webhook.getId(), organizationId, eventType, body, eventId));
            } catch (JacksonException e) {
                // Unserializable payload is our bug, not the receiver's. Never queue
                // something that can never be sent.
                log.error("Could not serialize {} payload; dropping delivery", eventType, e);
            }
        }
    }

    private java.util.Optional<UUID> organizationOf(UUID projectId) {
        return projects.findById(projectId)
                .map(com.charlie.ikibaho.project.ProjectSummary::organizationId);
    }
}
