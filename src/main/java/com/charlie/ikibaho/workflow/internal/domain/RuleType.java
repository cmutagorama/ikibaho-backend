package com.charlie.ikibaho.workflow.internal.domain;

/**
 * Registry keys. Adding a constant here means adding a bean with the same key.
 */
public enum RuleType {
    // conditions — decide whether a transition is offered at all
    PERMISSION,
    ASSIGNEE_ONLY,
    REPORTER_ONLY,
    PROJECT_ROLE,
    // validators — decide whether this particular attempt is legal
    FIELD_REQUIRED,
    NO_OPEN_SUBTASKS,
    // post functions — side effects after the status has changed
    ASSIGN_TO_CURRENT_USER,
    CLEAR_ASSIGNEE
}
