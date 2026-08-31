package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.workflow.TransitionEffect;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;

import java.util.List;

/**
 * Runs inside the same transaction as the status change.
 */
public interface TransitionPostFunction {
    RuleType type();

    List<TransitionEffect> apply(TransitionContext context);
}
