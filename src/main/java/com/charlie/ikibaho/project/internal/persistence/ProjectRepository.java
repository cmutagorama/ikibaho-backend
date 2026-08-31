package com.charlie.ikibaho.project.internal.persistence;

import com.charlie.ikibaho.project.internal.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByOrganizationIdAndKey(UUID organizationId, String key);

    boolean existsByOrganizationIdAndKey(UUID organizationId, String key);

    /**
     * Atomic per-project counter. Takes a row lock for the transaction, which
     * serializes issue creation within one project -- exactly the semantics we want.
     * Native query because JPQL has no RETURNING.
     */
    @Query(value = """
            UPDATE project SET issue_counter = issue_counter + 1
            WHERE id = :projectId
            RETURNING issue_counter
            """, nativeQuery = true)
    Long incrementIssueCounter(@Param("projectId") UUID projectId);

    /**
     * Sets both columns in one statement. Doing it via the entity would rely on
     * Hibernate's flush ordering to emit the deleted_at UPDATE before the
     * soft-delete UPDATE -- correct, but not worth depending on.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE project SET deleted = true
            WHERE id = :id AND deleted = false
            """, nativeQuery = true)
    int softDelete(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE project SET deleted = false
            WHERE id = :id AND deleted = true
            """, nativeQuery = true)
    int restore(@Param("id") UUID id);

    /**
     * Deleted projects are invisible to JPA, so this must be native.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM project
                WHERE organization_id = :organizationId AND key = :key AND deleted = false
            )
            """, nativeQuery = true)
    boolean keyInUse(@Param("organizationId") UUID organizationId, @Param("key") String key);
}
