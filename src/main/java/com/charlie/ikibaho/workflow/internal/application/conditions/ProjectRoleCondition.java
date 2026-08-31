package com.charlie.ikibaho.workflow.internal.application.conditions;

import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.workflow.internal.application.TransitionCondition;
import com.charlie.ikibaho.workflow.internal.application.TransitionContext;
import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

@Component
public class ProjectRoleCondition implements TransitionCondition {
    private final ProjectService projects;

    ProjectRoleCondition(ProjectService projects) {
        this.projects = projects;
    }

    @Override
    public RuleType type() {
        return RuleType.PROJECT_ROLE;
    }

    @Override
    public boolean isSatisfied(TransitionContext ctx) {
        String roleName = (String) ctx.config().get("roleName");
        if (roleName == null) return false;
        return projects.hasRole(ctx.actorId(), ctx.issue().projectId(), roleName);
    }
}
