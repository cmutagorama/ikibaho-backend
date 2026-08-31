package com.charlie.ikibaho.workflow.internal.persistence;

import com.charlie.ikibaho.workflow.internal.domain.RuleKind;
import com.charlie.ikibaho.workflow.internal.domain.TransitionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransitionRuleRepository extends JpaRepository<TransitionRule, UUID> {
    List<TransitionRule> findByTransitionIdAndKind(UUID transitionId, RuleKind kind);

    /**
     * Batch form: listing transitions must not issue one query per transition.
     */
    List<TransitionRule> findByTransitionIdInAndKind(Collection<UUID> transitionIds, RuleKind kind);
}
