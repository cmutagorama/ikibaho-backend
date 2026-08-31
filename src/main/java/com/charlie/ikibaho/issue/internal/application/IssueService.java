package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.events.IssueAssigned;
import com.charlie.ikibaho.issue.events.IssueCreated;
import com.charlie.ikibaho.issue.events.IssueDeleted;
import com.charlie.ikibaho.issue.events.IssueUpdated;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.domain.IssueLink;
import com.charlie.ikibaho.issue.internal.domain.IssueType;
import com.charlie.ikibaho.issue.internal.domain.LinkType;
import com.charlie.ikibaho.issue.internal.persistence.IssueLinkRepository;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.persistence.IssueRepository;
import com.charlie.ikibaho.issue.internal.persistence.IssueTypeRepository;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.issue.internal.web.dto.UpdateIssueCommand;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.project.*;
import com.charlie.ikibaho.workflow.WorkflowService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IssueService {
    private final IssueRepository issues;
    private final IssueTypeRepository types;
    private final IssueLinkRepository links;
    private final ProjectService projects;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;
    private final IssueMetadataProvisioning provisioning;
    private final CustomFieldValidator customFields;
    private final WorkflowService workflows;
    private final IssueQueries issueQueries;
    private final ApplicationEventPublisher events;
    private final IssueRankingService ranking;

    IssueService(
            IssueRepository issues,
            IssueTypeRepository types,
            IssueLinkRepository links,
            ProjectService projects,
            PermissionService permissions,
            IssueContextResolver contexts,
            IssueMetadataProvisioning provisioning,
            CustomFieldValidator customFields,
            WorkflowService workflows,
            IssueQueries issueQueries,
            ApplicationEventPublisher events,
            IssueRankingService ranking
    ) {
        this.issues = issues;
        this.types = types;
        this.links = links;
        this.projects = projects;
        this.permissions = permissions;
        this.contexts = contexts;
        this.provisioning = provisioning;
        this.customFields = customFields;
        this.workflows = workflows;
        this.issueQueries = issueQueries;
        this.events = events;
        this.ranking = ranking;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Transactional
    public Issue create(UUID projectId, UUID actorId, CreateIssueCommand cmd) {
        permissions.require(actorId, projectId, Permission.CREATE_ISSUE);

        ProjectSummary project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
        UUID orgId = project.organizationId();

        provisioning.ensureDefaults(orgId);

        IssueType type = types.findByIdAndOrganizationId(cmd.typeId(), orgId)
                .orElseThrow(() -> new NotFoundException("IssueType", cmd.typeId()));

        // The workflow decides where a new issue starts, not the lowest-positioned status.
        UUID initialStatusId = workflows.initialStatusFor(projectId, orgId);

        customFields.validate(orgId, projectId, cmd.customFields());

        // Atomic per-project counter. Serializes creation within one project, which
        // is exactly the semantics a gapless key sequence requires.
        long number = projects.nextIssueNumber(projectId);
        String key = project.key() + "-" + number;

        Issue issue = new Issue(orgId, projectId, key, number,
                type.getId(), initialStatusId, cmd.summary(), actorId);

        if (cmd.description() != null) issue.changeDescription(cmd.description());
        if (cmd.priority() != null) issue.changePriority(cmd.priority());
        if (cmd.assigneeId() != null) issue.assignTo(cmd.assigneeId());
        if (cmd.storyPoints() != null) issue.changeStoryPoints(cmd.storyPoints());
        if (cmd.dueDate() != null) issue.changeDueDate(cmd.dueDate());
        if (cmd.parentId() != null) applyParent(issue, cmd.parentId(), type, projectId);
        if (cmd.customFields() != null) cmd.customFields().forEach(issue::setCustomField);

        // Lands at the bottom of the backlog. Assigned here rather than by a
        // trigger so the rank is present the moment the issue is returned.
        issue.changeRank(ranking.rankForNewIssue(projectId));

        Issue saved = issues.save(issue);

        Instant now = Instant.now();
        events.publishEvent(new IssueCreated(saved.getId(), projectId, orgId, saved.getIssueKey(),
                saved.getSummary(), actorId, saved.getAssigneeId(), now));

        // Creating an issue already assigned is an assignment too. Publishing both
        // means notification does not need a special case for "assigned at birth".
        if (saved.getAssigneeId() != null) {
            events.publishEvent(new IssueAssigned(saved.getId(), projectId, saved.getIssueKey(),
                    saved.getSummary(), null, saved.getAssigneeId(), actorId, now));
        }

        return saved;
    }

    public Issue getForUser(UUID issueId, UUID actorId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);
        return load(issueId);
    }

    @Transactional
    public Issue update(UUID issueId, UUID actorId, UpdateIssueCommand cmd) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.EDIT_ISSUE);

        Issue issue = load(issueId);
        checkVersion(issue, cmd.version());

        if (cmd.summary() != null) issue.changeSummary(cmd.summary());
        if (cmd.description() != null) issue.changeDescription(cmd.description());
        if (cmd.priority() != null) issue.changePriority(cmd.priority());
        if (cmd.storyPoints() != null) issue.changeStoryPoints(cmd.storyPoints());
        if (cmd.dueDate() != null) issue.changeDueDate(cmd.dueDate());

        if (cmd.typeId() != null) {
            types.findByIdAndOrganizationId(cmd.typeId(), issue.getOrganizationId())
                    .orElseThrow(() -> new NotFoundException("IssueType", cmd.typeId()));
            issue.changeType(cmd.typeId());
        }

        if (cmd.customFields() != null) {
            customFields.validate(issue.getOrganizationId(), issue.getProjectId(), cmd.customFields());
            cmd.customFields().forEach(issue::setCustomField);
        }

        // Published after the mutations so a listener re-reading the issue sees the
        // new state. The transaction has not committed yet, but @ApplicationModuleListener
        // only runs after it does.
        events.publishEvent(new IssueUpdated(issue.getId(), issue.getProjectId(), issue.getIssueKey(), actorId, Instant.now()));
        return issue;   // dirty checking flushes at commit; @Version guards the UPDATE
    }

    @Transactional
    public Issue assign(UUID issueId, UUID actorId, UUID assigneeId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.ASSIGN_ISSUE);

        Issue issue = load(issueId);
        UUID previousAssigneeId = issue.getAssigneeId();
        if (assigneeId == null) issue.unassign();
        else issue.assignTo(assigneeId);

        // Reassigning to the current assignee is a no-op, not news.
        if (!Objects.equals(previousAssigneeId, issue.getAssigneeId())) {
            events.publishEvent(new IssueAssigned(issue.getId(), issue.getProjectId(),
                    issue.getIssueKey(), issue.getSummary(), previousAssigneeId,
                    issue.getAssigneeId(), actorId, Instant.now()));
        }
        return issue;
    }

    @Transactional
    public void delete(UUID issueId, UUID actorId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.DELETE_ISSUE);

        if (issues.existsByParentId(issueId)) {
            throw new ConflictException("Delete or re-parent the sub-tasks first");
        }

        // Read the key before deleting: after the soft delete the row is filtered
        // out of every query, so the event could not name what it removed.
        Issue issue = load(issueId);
        UUID projectId = issue.getProjectId();
        String issueKey = issue.getIssueKey();

        issues.deleteById(issueId);   // @SoftDelete turns this into an UPDATE

        events.publishEvent(new IssueDeleted(issueId, projectId, issueKey, actorId, Instant.now()));
    }

    @Transactional
    public IssueLink link(UUID sourceId, UUID actorId, UUID targetId, LinkType type) {
        IssueContext source = requireContext(sourceId);
        permissions.require(actorId, source, Permission.EDIT_ISSUE);

        // Both ends must be visible, or linking becomes an existence oracle.
        IssueContext target = requireContext(targetId);
        permissions.require(actorId, target, Permission.BROWSE_PROJECT);

        if (links.existsBySourceIdAndTargetIdAndLinkType(sourceId, targetId, type)) {
            throw new ConflictException("That link already exists");
        }
        return links.save(new IssueLink(sourceId, targetId, type));
    }

    /**
     * Project-scoped list. The permission check happens here rather than in the
     * controller so every caller is covered, and it yields 404 for a project the
     * user cannot browse -- consistent with every other project-scoped read.
     */
    public List<IssueQueries.IssueRow> listForProject(UUID projectId, UUID actorId,
                                                      IssueFilter filter,
                                                      Instant cursorCreatedAt, UUID cursorId,
                                                      int limit) {
        permissions.require(actorId, projectId, Permission.BROWSE_PROJECT);
        IssueFilter f = filter == null ? IssueFilter.NONE : filter;

        return issueQueries.listByProject(projectId, f.assigneeId(), f.statusId(),
                f.statusCategory(), f.typeId(), blankToNull(f.search()),
                cursorCreatedAt, cursorId, limit);
    }

    public List<IssueLink> linksOf(UUID issueId, UUID actorId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);
        return links.findBySourceIdOrTargetId(issueId, issueId);
    }

    private void applyParent(Issue issue, UUID parentId, IssueType type, UUID projectId) {
        Issue parent = issues.findById(parentId)
                .orElseThrow(() -> new NotFoundException("Issue", parentId));
        if (!parent.getProjectId().equals(projectId)) {
            throw new ValidationException("Parent must be in the same project");
        }
        IssueType parentType = types.findById(parent.getTypeId()).orElseThrow();
        if (parentType.getHierarchyLevel() <= type.getHierarchyLevel()) {
            throw new ValidationException(
                    "A " + type.getName() + " cannot be a child of a " + parentType.getName());
        }
        issue.changeParent(parentId);
    }

    private void checkVersion(Issue issue, long expected) {
        if (issue.getVersion() != expected) {
            throw new OptimisticLockingFailureException(
                    "Issue " + issue.getIssueKey() + " was modified by someone else");
        }
    }

    private IssueContext requireContext(UUID issueId) {
        return contexts.resolve(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }

    private Issue load(UUID issueId) {
        return issues.findById(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }

    /**
     * Filters for the project backlog / issue list screen. Any field may be null.
     */
    public record IssueFilter(UUID assigneeId, UUID statusId, String statusCategory,
                              UUID typeId, String search) {
        public static final IssueFilter NONE = new IssueFilter(null, null, null, null, null);
    }
}
