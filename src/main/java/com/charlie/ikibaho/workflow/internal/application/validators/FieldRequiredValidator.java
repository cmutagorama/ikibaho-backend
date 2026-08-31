package com.charlie.ikibaho.workflow.internal.application.validators;

import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.workflow.internal.application.*;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

@Component
public class FieldRequiredValidator implements TransitionValidator {
    @Override public RuleType type() { return RuleType.FIELD_REQUIRED; }

    @Override
    public void validate(TransitionContext ctx) {
        String field = (String) ctx.config().get("field");
        if (field == null) return;

        boolean present = switch (field) {
            case "assignee" -> ctx.issue().assigneeId() != null;
            default -> {
                Object value = ctx.issue().customFields().get(field);
                yield value != null && !(value instanceof String s && s.isBlank());
            }
        };

        if (!present) {
            String label = (String) ctx.config().getOrDefault("label", field);
            throw new ValidationException("'" + label + "' must be set before this transition");
        }
    }
}
