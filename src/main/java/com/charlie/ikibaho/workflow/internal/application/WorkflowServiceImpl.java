package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.project.ProjectSummary;
import com.charlie.ikibaho.workflow.AvailableTransition;
import com.charlie.ikibaho.workflow.IssueFacts;
import com.charlie.ikibaho.workflow.TransitionEffect;
import com.charlie.ikibaho.workflow.WorkflowService;
import com.charlie.ikibaho.workflow.internal.domain.RuleKind;
import com.charlie.ikibaho.workflow.internal.domain.Transition;
import com.charlie.ikibaho.workflow.internal.domain.TransitionRule;
import com.charlie.ikibaho.workflow.internal.domain.Workflow;
import com.charlie.ikibaho.workflow.internal.persistence.StatusNameLookup;
import com.charlie.ikibaho.workflow.internal.persistence.TransitionRepository;
import com.charlie.ikibaho.workflow.internal.persistence.TransitionRuleRepository;
import com.charlie.ikibaho.workflow.internal.persistence.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflows;
    private final TransitionRepository transitions;
    private final TransitionRuleRepository rules;
    private final RuleRegistry registry;
    private final StatusNameLookup statusNames;
    private final ProjectService projects;

    WorkflowServiceImpl(
            WorkflowRepository workflows,
            TransitionRepository transitions,
            TransitionRuleRepository rules,
            RuleRegistry registry,
            StatusNameLookup statusNames,
            ProjectService projects) {
        this.workflows = workflows;
        this.transitions = transitions;
        this.rules = rules;
        this.registry = registry;
        this.statusNames = statusNames;
        this.projects = projects;
    }

    @Override
    public List<AvailableTransition> availableFor(IssueFacts issue, UUID currentStatusId, UUID actorId) {
        UUID workflowId = workflowIdFor(issue.projectId());

        // Same reachability rule the execute path uses -- one definition, no drift.
        List<Transition> candidates = transitions.findApplicable(workflowId, currentStatusId).stream()
                .filter(t -> t.isAvailableFrom(currentStatusId))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // One query for every candidate's conditions, not one per candidate.
        Map<UUID, List<TransitionRule>> conditionsByTransition = rules
                .findByTransitionIdInAndKind(candidates.stream().map(Transition::getId).toList(),
                        RuleKind.CONDITION)
                .stream()
                .collect(Collectors.groupingBy(TransitionRule::getTransitionId));

        Map<UUID, String> names = statusNames.byIds(
                candidates.stream().map(Transition::getToStatusId).collect(Collectors.toSet()));

        return candidates.stream()
                .filter(t -> conditionsPass(
                        conditionsByTransition.getOrDefault(t.getId(), List.of()),
                        issue, actorId, currentStatusId, t.getToStatusId()))
                .map(t -> new AvailableTransition(t.getId(), t.getName(), t.getToStatusId(),
                        names.get(t.getToStatusId())))
                .toList();
    }

    @Override
    public TransitionOutcome execute(IssueFacts issue, UUID currentStatusId,
                                     UUID transitionId, UUID actorId) {
        Transition t = transitions.findById(transitionId)
                .orElseThrow(() -> new NotFoundException("Transition", transitionId));

        // Tenancy: a transition id belonging to another workflow must look nonexistent.
        if (!t.getWorkflowId().equals(workflowIdFor(issue.projectId()))) {
            throw new NotFoundException("Transition", transitionId);
        }

        // Re-check reachability: between rendering the list and clicking, someone else
        // may have moved the issue. Without this you get lost updates on status.
        if (t.targets(currentStatusId)) {
            throw new ConflictException("The issue is already in that status");
        }
        if (!t.isAvailableFrom(currentStatusId)) {
            throw new ConflictException("That transition is not available from the current status");
        }

        List<TransitionRule> conditions =
                rules.findByTransitionIdAndKind(transitionId, RuleKind.CONDITION);
        if (!conditionsPass(conditions, issue, actorId, currentStatusId, t.getToStatusId())) {
            throw new ForbiddenException("You cannot perform that transition");
        }

        TransitionContext base = new TransitionContext(issue, actorId, currentStatusId,
                t.getToStatusId(), Map.of());

        rules.findByTransitionIdAndKind(transitionId, RuleKind.VALIDATOR)
                .forEach(rule -> registry.validator(rule.getRuleType())
                        .ifPresent(v -> v.validate(base.withConfig(rule.getConfig()))));

        List<TransitionEffect> effects = rules
                .findByTransitionIdAndKind(transitionId, RuleKind.POST_FUNCTION).stream()
                .flatMap(rule -> registry.postFunction(rule.getRuleType())
                        .map(f -> f.apply(base.withConfig(rule.getConfig())).stream())
                        .orElseGet(Stream::empty))
                .toList();

        return new TransitionOutcome(t.getToStatusId(), effects);
    }

    @Override
    public UUID initialStatusFor(UUID projectId, UUID organizationId) {
        return workflows.findById(workflowIdFor(projectId))
                .map(Workflow::getInitialStatusId)
                .orElseThrow(() -> new IllegalStateException("No workflow for project " + projectId));
    }

    /**
     * Conditions receive the issue's ACTUAL current status as fromStatusId, not the
     * transition's declared one -- that is null for global transitions, so a condition
     * inspecting it would silently see nothing.
     */
    private boolean conditionsPass(List<TransitionRule> conditions, IssueFacts issue,
                                   UUID actorId, UUID fromStatusId, UUID toStatusId) {
        if (conditions.isEmpty()) {
            return true;
        }
        TransitionContext base =
                new TransitionContext(issue, actorId, fromStatusId, toStatusId, Map.of());
        return conditions.stream().allMatch(rule -> registry.condition(rule.getRuleType())
                .map(c -> c.isSatisfied(base.withConfig(rule.getConfig())))
                // Unknown rule type denies: a typo in configuration must not grant access.
                .orElse(false));
    }

    /**
     * The project's own workflow if it has one, otherwise the organization's default.
     * Resolving the org costs a second query, but only on the fallback path -- which
     * is the normal path until a project is given a workflow of its own.
     */
    private UUID workflowIdFor(UUID projectId) {
        return projects.workflowIdFor(projectId)
                .or(() -> projects.findById(projectId)
                        .map(ProjectSummary::organizationId)
                        .flatMap(workflows::findByOrganizationIdAndDefaultWorkflowTrue)
                        .map(Workflow::getId))
                .orElseThrow(() -> new IllegalStateException(
                        "No workflow configured for project " + projectId));
    }
}
