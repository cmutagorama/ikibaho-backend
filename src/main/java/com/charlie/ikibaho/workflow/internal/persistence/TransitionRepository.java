package com.charlie.ikibaho.workflow.internal.persistence;

import com.charlie.ikibaho.workflow.internal.domain.Transition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransitionRepository extends JpaRepository<Transition, UUID> {
    /**
     * Global transitions plus those leaving the given status.
     */
    @Query("""
            SELECT t FROM Transition t
            WHERE t.workflowId = :workflowId
              AND (t.fromStatusId IS NULL OR t.fromStatusId = :fromStatusId)
            ORDER BY t.name
            """)
    List<Transition> findApplicable(@Param("workflowId") UUID workflowId,
                                    @Param("fromStatusId") UUID fromStatusId);

    List<Transition> findByWorkflowId(UUID workflowId);
}
