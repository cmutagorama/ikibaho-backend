package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Unambiguous again: email is globally unique, so this returns the account. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Everyone in a workspace, joined through membership rather than read off a
     * column on the user. Includes invited and deactivated members -- the admin
     * screen needs both, and filters in the service.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id IN (SELECT m.userId FROM OrganizationMember m WHERE m.organizationId = :organizationId)
            ORDER BY u.displayName ASC
            """)
    List<User> findByOrganization(UUID organizationId);
}
