package com.charlie.ikibaho.platform.domain;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME) // UUIDv7 — time-ordered
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isNew() {
        return id == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        // Compare the Hibernate-unwrapped types: a lazy proxy's getClass() is a
        // generated subclass, so a proxy would never equal its own entity.
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(other))) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        // Constant per type: stable across the transient -> persistent transition,
        // which is what breaks HashSet membership if you hash on id.
        return Hibernate.getClass(this).hashCode();
    }
}
