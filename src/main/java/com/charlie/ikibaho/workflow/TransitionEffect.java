package com.charlie.ikibaho.workflow;

import java.util.UUID;

/**
 * What a post-function wants done to the issue. The workflow module cannot touch
 * issues itself -- issue already depends on workflow, so the arrow must not reverse.
 */
public sealed interface TransitionEffect {
    record AssignTo(UUID userId) implements TransitionEffect {
    }

    record Unassign() implements TransitionEffect {
    }
}
