package com.charlie.ikibaho.integration.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import com.charlie.ikibaho.platform.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A subscription: send these event types to this URL, signed with this secret.
 */
@Entity
@Table(name = "webhook")
public class Webhook extends BaseEntity {
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secret;

    private String description;

    /**
     * Mapped as a native text[] rather than a join table: it is only ever read
     * whole with its webhook and never joined against.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", nullable = false)
    private String[] eventTypes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    protected Webhook() {
    }

    public Webhook(UUID organizationId, String url, String secret, String description,
                   Set<String> eventTypes, UUID createdBy) {
        this.organizationId = organizationId;
        this.url = validateUrl(url);
        this.secret = secret;
        this.description = description;
        this.eventTypes = requireEvents(eventTypes);
        this.createdBy = createdBy;
        this.active = true;
    }

    /**
     * Rejects anything that is not an absolute http(s) URL.
     * <p>
     * Note what this does NOT do: block private addresses. A webhook pointing at
     * 169.254.169.254 or localhost turns this server into a proxy for scanning
     * its own network -- server-side request forgery. Blocking it properly means
     * resolving the host at delivery time and re-checking after redirects, which
     * belongs in the sender, not here. See HttpWebhookSender.
     */
    private static String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("A webhook URL is required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("That is not a valid URL");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new ValidationException("Webhook URLs must be absolute");
        }
        if (!uri.getScheme().equals("https") && !uri.getScheme().equals("http")) {
            throw new ValidationException("Webhook URLs must be http or https");
        }
        return uri.toString();
    }

    private static String[] requireEvents(Set<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new ValidationException("Subscribe to at least one event type");
        }
        for (String type : eventTypes) {
            if (!WebhookEvent.isKnown(type)) {
                throw new ValidationException("Unknown event type '" + type + "'. Known: "
                        + WebhookEvent.known());
            }
        }
        return eventTypes.toArray(String[]::new);
    }

    public void update(String url, String description, Set<String> eventTypes, boolean active) {
        this.url = validateUrl(url);
        this.description = description;
        this.eventTypes = requireEvents(eventTypes);
        this.active = active;
    }

    public boolean isSubscribedTo(String eventType) {
        for (String subscribed : eventTypes) {
            if (subscribed.equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getEventTypes() {
        return List.of(eventTypes);
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
