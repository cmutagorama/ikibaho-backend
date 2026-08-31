package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.workflow.WorkflowProvisioning;
import com.charlie.ikibaho.workflow.internal.domain.Transition;
import com.charlie.ikibaho.workflow.internal.domain.Workflow;
import com.charlie.ikibaho.workflow.internal.persistence.TransitionRepository;
import com.charlie.ikibaho.workflow.internal.persistence.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowProvisioningImpl implements WorkflowProvisioning {
    static final String DEFAULT_NAME = "Default Workflow";
    private final WorkflowRepository workflows;
    private final TransitionRepository transitions;

    WorkflowProvisioningImpl(WorkflowRepository workflows, TransitionRepository transitions) {
        this.workflows = workflows;
        this.transitions = transitions;
    }

    @Override
    @Transactional
    public UUID ensureDefaultWorkflow(UUID organizationId, List<StatusRef> statuses) {
        var existing = workflows.findByOrganizationIdAndDefaultWorkflowTrue(organizationId);
        if (existing.isPresent()) return existing.get().getId();

        if (statuses.isEmpty()) {
            throw new IllegalStateException("Cannot build a workflow with no statuses");
        }
        List<StatusRef> ordered = statuses.stream()
                .sorted(Comparator.comparingInt(StatusRef::position))
                .toList();

        Workflow workflow = workflows.save(new Workflow(
                organizationId, DEFAULT_NAME, ordered.getFirst().id(), true));

        // Global transitions to every status: permissive by default. Tighten the graph
        // deliberately rather than discovering issues are stuck.
        ordered.forEach(s -> transitions.save(
                new Transition(workflow.getId(), s.name(), null, s.id())));

        return workflow.getId();
    }
}
