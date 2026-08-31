package com.charlie.ikibaho.workflow;

import java.util.List;
import java.util.UUID;

public interface WorkflowService {
    /**
     * Transitions whose conditions the actor satisfies, from the issue's current status.
     *//**/
    List<AvailableTransition> availableFor(IssueFacts issue, UUID currentStatusId, UUID actorId);

    /**
     * Validates, then returns the target status. Post-functions run via the returned handle.
     */
    TransitionOutcome execute(IssueFacts issue, UUID currentStatusId, UUID transitionId, UUID actorId);

    /**
     * Status a newly created issue starts in.
     */
    UUID initialStatusFor(UUID projectId, UUID organizationId);

    record TransitionOutcome(UUID toStatusId, List<TransitionEffect> effects) {
    }
}
