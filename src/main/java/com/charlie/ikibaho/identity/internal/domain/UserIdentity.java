package com.charlie.ikibaho.identity.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * A link between an Ikibaho account and an external identity provider.
 */
@Entity
@Table(name = "user_identity")
public class UserIdentity extends BaseEntity {
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private IdentityProvider provider;

    @Column(nullable = false, updatable = false)
    private String subject;

    /**
     * Recorded at link time so an audit can answer "which address was this?".
     */
    @Column(nullable = false)
    private String email;

    protected UserIdentity() {
    }

    public UserIdentity(UUID userId, IdentityProvider provider, String subject, String email) {
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
    }

    /**
     * The provider's address can change; the subject cannot.
     */
    public void refreshEmail(String email) {
        this.email = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }
}
