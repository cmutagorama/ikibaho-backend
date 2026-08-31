package com.charlie.ikibaho.workflow.internal.application.validators;

import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.application.TransitionValidator;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

@Component
public class NoOpenSubtasksValidator implements TransitionValidator {
    public RuleType type() {
        return RuleType.NO_OPEN_SUBTASKS;
    }

    public void validate(TransitionContext ctx) {
        int open = ctx.issue().openSubtaskCount();
        if (open > 0) {
            throw new ValidationException("This issue has " + open + " unresolved sub-task(s)");
        }
    }
}
