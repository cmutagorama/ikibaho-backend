package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.IdentityProvider;
import com.charlie.ikibaho.identity.internal.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    /**
     * The sign-in lookup. Keyed on subject, never email.
     */
    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, IdentityProvider provider);

    List<UserIdentity> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
