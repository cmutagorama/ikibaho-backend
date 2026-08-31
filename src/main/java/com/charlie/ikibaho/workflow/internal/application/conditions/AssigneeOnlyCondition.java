package com.charlie.ikibaho.workflow.internal.application.conditions;

import com.charlie.ikibaho.workflow.internal.application.TransitionCondition;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

@Component
public class AssigneeOnlyCondition implements TransitionCondition {
    @Override
    public RuleType type() {
        return RuleType.ASSIGNEE_ONLY;
    }

    public boolean isSatisfied(TransitionContext ctx) {
        // Unassigned means nobody satisfies this -- the transition simply is not offered.
        return ctx.actorId().equals(ctx.issue().assigneeId());
    }
}
