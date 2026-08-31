package com.charlie.ikibaho.identity.internal.persistence;

import com.charlie.ikibaho.identity.internal.domain.MembershipStatus;
import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrganizationMember> findByOrganizationId(UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    /**
     * The permission predicate other modules ask through UserService.
     *
     * ACTIVE only: an invited-but-unaccepted member must not be assignable to
     * issues, and a deactivated one must stop being so immediately.
     */
    boolean existsByUserIdAndOrganizationIdAndStatus(UUID userId, UUID organizationId,
                                                    MembershipStatus status);

    /** The workspaces a person may sign in to, oldest membership first. */
    @Query("""
            SELECT m FROM OrganizationMember m
            WHERE m.userId = :userId
              AND m.status = com.charlie.ikibaho.identity.internal.domain.MembershipStatus.ACTIVE
            ORDER BY m.createdAt ASC
            """)
    List<OrganizationMember> activeFor(UUID userId);
}
