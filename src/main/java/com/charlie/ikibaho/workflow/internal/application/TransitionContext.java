package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.workflow.IssueFacts;

import java.util.Map;
import java.util.UUID;

/**
 * Everything a rule may inspect. Deliberately a record: rules cannot mutate it.
 */
public record TransitionContext(
        IssueFacts issue,
        UUID actorId,
        UUID fromStatusId,
        UUID toStatusId,
        Map<String, Object> config) {
    TransitionContext withConfig(Map<String, Object> newConfig) {
        return new TransitionContext(issue, actorId, fromStatusId, toStatusId, newConfig);
    }
}
