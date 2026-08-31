package com.charlie.ikibaho.search.internal.persistence;

import com.charlie.ikibaho.search.internal.domain.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {
    /**
     * Mine, plus everyone else's shared ones, in one query.
     */
    @Query("""
            SELECT f FROM SavedFilter f
            WHERE f.organizationId = :organizationId
              AND (f.ownerId = :userId OR f.shared = true)
            ORDER BY f.name ASC
            """)
    List<SavedFilter> visibleTo(UUID organizationId, UUID userId);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);
}
