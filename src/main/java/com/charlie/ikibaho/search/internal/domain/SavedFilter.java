package com.charlie.ikibaho.search.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A named JQL query.
 * <p>
 * The filter stores the query, never its results. Whoever runs it runs it under
 * their own permissions, so sharing a filter can never widen what the reader is
 * allowed to see -- it can only show them fewer rows than the author sees.
 */
@Entity
@Table(name = "saved_filter")
public class SavedFilter extends BaseEntity {
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String jql;

    private String description;

    @Column(nullable = false)
    private boolean shared;

    protected SavedFilter() {
    }

    public SavedFilter(UUID organizationId, UUID ownerId, String name, String jql,
                       String description, boolean shared) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.name = name;
        this.jql = jql;
        this.description = description;
        this.shared = shared;
    }

    public void update(String name, String jql, String description, boolean shared) {
        this.name = name;
        this.jql = jql;
        this.description = description;
        this.shared = shared;
    }

    public boolean isVisibleTo(UUID userId) {
        return shared || ownerId.equals(userId);
    }

    /**
     * Only the owner may edit or delete, however widely the filter is shared.
     */
    public void requireOwnedBy(UUID userId) {
        if (!ownerId.equals(userId)) {
            throw new ForbiddenException("Only the filter's owner can change it");
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getJql() {
        return jql;
    }

    public String getDescription() {
        return description;
    }

    public boolean isShared() {
        return shared;
    }
}
