package com.charlie.ikibaho.workflow.internal.application.conditions;

import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.workflow.internal.application.TransitionCondition;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PermissionCondition implements TransitionCondition {
    private static final Logger log = LoggerFactory.getLogger(PermissionCondition.class);
    private final PermissionService permissions;

    PermissionCondition(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public RuleType type() {
        return RuleType.PERMISSION;
    }

    public boolean isSatisfied(TransitionContext ctx) {
        String name = (String) ctx.config().getOrDefault("permission", Permission.TRANSITION_ISSUE.name());
        try {
            var issue = new IssueContext(ctx.issue().issueId(), ctx.issue().projectId(),
                    ctx.issue().reporterId(), ctx.issue().assigneeId());
            return permissions.hasPermission(ctx.actorId(), issue, Permission.valueOf(name));
        } catch (IllegalArgumentException e) {
            // A misconfigured rule denies. Failing open would silently grant the transition.
            log.warn("Unknown permission '{}' in transition rule", name);
            return false;
        }
    }
}
