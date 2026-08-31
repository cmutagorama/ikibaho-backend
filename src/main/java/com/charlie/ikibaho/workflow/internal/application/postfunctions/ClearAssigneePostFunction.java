package com.charlie.ikibaho.workflow.internal.application.postfunctions;

import com.charlie.ikibaho.workflow.TransitionEffect;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.application.TransitionPostFunction;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClearAssigneePostFunction implements TransitionPostFunction {
    @Override
    public RuleType type() {
        return RuleType.CLEAR_ASSIGNEE;
    }

    @Override
    public List<TransitionEffect> apply(TransitionContext ctx) {
        return List.of(new TransitionEffect.Unassign());
    }
}
