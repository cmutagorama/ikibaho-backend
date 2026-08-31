package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.workflow.internal.domain.RuleType;

/**
 * Explains why a visible transition was refused. Throws ValidationException.
 */
public interface TransitionValidator {
    RuleType type();

    void validate(TransitionContext context);
}
