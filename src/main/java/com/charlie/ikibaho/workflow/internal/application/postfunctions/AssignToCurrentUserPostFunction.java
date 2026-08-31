package com.charlie.ikibaho.workflow.internal.application.postfunctions;

import com.charlie.ikibaho.workflow.TransitionEffect;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.application.TransitionPostFunction;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssignToCurrentUserPostFunction implements TransitionPostFunction {
    @Override
    public RuleType type() {
        return RuleType.ASSIGN_TO_CURRENT_USER;
    }

    @Override
    public List<TransitionEffect> apply(TransitionContext ctx) {
        boolean onlyIfUnassigned = Boolean.TRUE.equals(ctx.config().get("onlyIfUnassigned"));
        if (onlyIfUnassigned && ctx.issue().assigneeId() != null) {
            return List.of();
        }

        return List.of(new TransitionEffect.AssignTo(ctx.actorId()));
    }
}
