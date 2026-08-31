package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now
            WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Ends this person's sessions in one workspace only.
     *
     * Being removed from one organization must not sign you out of the others.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now
            WHERE t.userId = :userId AND t.organizationId = :organizationId AND t.revokedAt IS NULL
            """)
    int revokeForUserInOrganization(@Param("userId") UUID userId,
                                    @Param("organizationId") UUID organizationId,
                                    @Param("now") Instant now);
}
