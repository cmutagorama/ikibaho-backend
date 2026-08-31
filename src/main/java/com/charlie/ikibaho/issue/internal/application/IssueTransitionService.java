package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.events.IssueAssigned;
import com.charlie.ikibaho.issue.events.IssueTransitioned;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.domain.Status;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.persistence.IssueRepository;
import com.charlie.ikibaho.issue.internal.persistence.StatusRepository;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.workflow.AvailableTransition;
import com.charlie.ikibaho.workflow.IssueFacts;
import com.charlie.ikibaho.workflow.TransitionEffect;
import com.charlie.ikibaho.workflow.WorkflowService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything about moving an issue through its workflow.
 *
 * Split out of IssueService, which had grown to cover CRUD, links, listing and
 * workflow execution at once. Transitions are the one part with their own
 * collaborator (WorkflowService), their own permission, and -- since phase 6 --
 * their own events, so they earn a seam.
 */
@Service
@Transactional(readOnly = true)
public class IssueTransitionService {
    private final IssueRepository issues;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;
    private final WorkflowService workflows;
    private final IssueQueries issueQueries;
    private final StatusRepository statuses;
    private final ApplicationEventPublisher events;

    IssueTransitionService(IssueRepository issues, PermissionService permissions,
                           IssueContextResolver contexts, WorkflowService workflows,
                           IssueQueries issueQueries, StatusRepository statuses,
                           ApplicationEventPublisher events) {
        this.issues = issues;
        this.permissions = permissions;
        this.contexts = contexts;
        this.workflows = workflows;
        this.issueQueries = issueQueries;
        this.statuses = statuses;
        this.events = events;
    }

    @Transactional
    public Issue transition(UUID issueId, UUID actorId, UUID transitionId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.TRANSITION_ISSUE);

        Issue issue = load(issueId);
        IssueFacts facts = factsFor(issue);

        // Captured before the mutation: once changeStatus runs, the origin of the
        // move is gone, and it is the half of the audit record that matters.
        UUID fromStatusId = issue.getStatusId();
        UUID previousAssigneeId = issue.getAssigneeId();

        var outcome = workflows.execute(facts, fromStatusId, transitionId, actorId);
        issue.changeStatus(outcome.toStatusId());

        // Same transaction as the status change: effects commit or roll back together.
        for (TransitionEffect effect : outcome.effects()) {
            switch (effect) {
                case TransitionEffect.AssignTo a -> issue.assignTo(a.userId());
                case TransitionEffect.Unassign ignored -> issue.unassign();
            }
        }

        Instant now = Instant.now();
        events.publishEvent(new IssueTransitioned(issue.getId(), issue.getProjectId(),
                issue.getIssueKey(),
                fromStatusId, statusName(fromStatusId),
                outcome.toStatusId(), statusName(outcome.toStatusId()),
                transitionId, actorId, now));

        // A post-function may have reassigned. Announce that as an assignment in its
        // own right, so notification does not have to know what post-functions exist.
        if (!Objects.equals(previousAssigneeId, issue.getAssigneeId())) {
            events.publishEvent(new IssueAssigned(issue.getId(), issue.getProjectId(),
                    issue.getIssueKey(), issue.getSummary(), previousAssigneeId,
                    issue.getAssigneeId(), actorId, now));
        }

        return issue;
    }

    public List<AvailableTransition> availableTransitions(UUID issueId, UUID actorId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);
        Issue issue = load(issueId);
        return workflows.availableFor(factsFor(issue), issue.getStatusId(), actorId);
    }

    /** Statuses are issue-module state, so the name is resolved here, at publish time. */
    private String statusName(UUID statusId) {
        return statuses.findById(statusId).map(Status::getName).orElse(null);
    }

    private IssueFacts factsFor(Issue issue) {
        int openSubtasks = issueQueries.countOpenSubtasks(issue.getId());
        return new IssueFacts(issue.getId(), issue.getProjectId(), issue.getReporterId(),
                issue.getAssigneeId(), openSubtasks, issue.getCustomFields());
    }

    private IssueContext requireContext(UUID issueId) {
        return contexts.resolve(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }

    private Issue load(UUID issueId) {
        return issues.findById(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }
}
