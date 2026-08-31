package com.charlie.ikibaho.workflow;

import java.util.List;
import java.util.UUID;

/**
 * Called by the issue module once an organization's statuses exist.
 */
public interface WorkflowProvisioning {
    UUID ensureDefaultWorkflow(UUID organizationId, List<StatusRef> statuses);

    record StatusRef(UUID id, String name, int position) {
    }
}
