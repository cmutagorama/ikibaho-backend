package com.charlie.ikibaho.workflow.internal.application.conditions;

import com.charlie.ikibaho.workflow.internal.application.TransitionCondition;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

@Component
public class ReporterOnlyCondition implements TransitionCondition {
    @Override
    public RuleType type() {
        return RuleType.REPORTER_ONLY;
    }

    @Override
    public boolean isSatisfied(TransitionContext ctx) {
        return ctx.actorId().equals(ctx.issue().reporterId());
    }
}
