package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.workflow.internal.domain.RuleType;

/**
 * Decides whether a transition is offered at all. Must never throw --
 * "not available" is a normal answer, not an error.
 */
public interface TransitionCondition {
    RuleType type();

    boolean isSatisfied(TransitionContext context);
}
