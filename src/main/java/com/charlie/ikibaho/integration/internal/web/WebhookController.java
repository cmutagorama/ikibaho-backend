package com.charlie.ikibaho.integration.internal.web;

import com.charlie.ikibaho.integration.WebhookCreated;
import com.charlie.ikibaho.integration.WebhookResponse;
import com.charlie.ikibaho.integration.internal.application.WebhookService;
import com.charlie.ikibaho.integration.internal.domain.WebhookEvent;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Site-admin only, throughout.
 *
 * A webhook receives every event in the organization, so creating one is
 * equivalent to granting a continuous read of everything -- not a project-level
 * decision.
 */
@RestController
@RequestMapping(ApiVersion.V1 + "/webhooks")
@Validated
@PreAuthorize("hasRole('ADMIN')")
class WebhookController {
    private final WebhookService webhooks;
    private final CurrentUser currentUser;

    WebhookController(WebhookService webhooks, CurrentUser currentUser) {
        this.webhooks = webhooks;
        this.currentUser = currentUser;
    }

    /** What a client may subscribe to. Static, but better read than guessed. */
    @GetMapping("/event-types")
    List<String> eventTypes() {
        return WebhookEvent.all();
    }

    @GetMapping
    List<WebhookResponse> list() {
        return webhooks.list(currentUser.requireOrganizationId());
    }

    @GetMapping("/{webhookId}")
    WebhookResponse get(@PathVariable UUID webhookId) {
        return webhooks.get(webhookId, currentUser.requireOrganizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WebhookCreated create(@Valid @RequestBody WebhookRequest req) {
        return webhooks.create(currentUser.requireOrganizationId(), currentUser.requireId(),
                req.url(), req.description(), req.eventTypes());
    }

    @PutMapping("/{webhookId}")
    WebhookResponse update(@PathVariable UUID webhookId, @Valid @RequestBody WebhookRequest req) {
        return webhooks.update(webhookId, currentUser.requireOrganizationId(),
                req.url(), req.description(), req.eventTypes(), req.active());
    }

    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID webhookId) {
        webhooks.delete(webhookId, currentUser.requireOrganizationId());
    }

    @GetMapping("/{webhookId}/deliveries")
    List<WebhookService.DeliveryView> deliveries(@PathVariable UUID webhookId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return webhooks.recentDeliveries(webhookId, currentUser.requireOrganizationId(),
                Math.clamp(limit, 1, 200));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void retry(@PathVariable UUID deliveryId) {
        webhooks.retry(deliveryId, currentUser.requireOrganizationId());
    }

    record WebhookRequest(@NotBlank String url, String description,
                          @NotEmpty Set<String> eventTypes, boolean active) {
    }
}
