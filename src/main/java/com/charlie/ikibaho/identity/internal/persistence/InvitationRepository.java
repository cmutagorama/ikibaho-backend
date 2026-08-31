package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findFirstByUserIdAndAcceptedAtIsNullAndRevokedAtIsNull(UUID userId);
}
