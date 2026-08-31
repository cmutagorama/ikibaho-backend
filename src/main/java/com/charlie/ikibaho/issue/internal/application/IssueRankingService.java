package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.persistence.IssueRepository;
import com.charlie.ikibaho.issue.internal.rank.LexoRank;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reordering, as a drag-and-drop produces it.
 * <p>
 * The client sends the two issues the dragged one landed between rather than a
 * numeric position. Positions are stale the moment anyone else reorders;
 * neighbours describe the intent directly, and two people dropping into
 * different gaps do not conflict.
 */
@Service
@Transactional(readOnly = true)
public class IssueRankingService {
    private final IssueRepository issues;
    private final IssueQueries issueQueries;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;

    IssueRankingService(IssueRepository issues, IssueQueries issueQueries,
                        PermissionService permissions, IssueContextResolver contexts) {
        this.issues = issues;
        this.issueQueries = issueQueries;
        this.permissions = permissions;
        this.contexts = contexts;
    }

    /**
     * Place an issue between two neighbours. Either neighbour may be null,
     * meaning the issue moved to the top or the bottom of the list.
     */
    @Transactional
    public Issue moveBetween(UUID issueId, UUID actorId, UUID afterId, UUID beforeId) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.EDIT_ISSUE);

        if (issueId.equals(afterId) || issueId.equals(beforeId)) {
            throw new ValidationException("An issue cannot be ranked relative to itself");
        }

        Issue issue = load(issueId);
        String after = neighbourRank(afterId, issue.getProjectId());
        String before = neighbourRank(beforeId, issue.getProjectId());

        try {
            issue.changeRank(LexoRank.between(after, before));
        } catch (IllegalArgumentException e) {
            // The neighbours the client sent are not actually adjacent in that
            // order -- almost always a stale board that someone else reordered.
            throw new ValidationException("Those issues are no longer in that order; refresh the board");
        }
        return issue;
    }

    /**
     * The rank for a newly created issue: the bottom of the backlog.
     * <p>
     * New work arriving at the top would silently reprioritise someone's backlog
     * every time anyone filed a bug.
     */
    public String rankForNewIssue(UUID projectId) {
        String lowest = issueQueries.lowestRank(projectId);
        return lowest == null ? LexoRank.initial() : LexoRank.after(lowest);
    }

    /**
     * Neighbours must live in the same project, or a drag could interleave two
     * projects' backlogs into an order neither board can render.
     */
    private String neighbourRank(UUID neighbourId, UUID projectId) {
        if (neighbourId == null) {
            return null;
        }
        Issue neighbour = load(neighbourId);
        if (!neighbour.getProjectId().equals(projectId)) {
            throw new ValidationException("Neighbouring issues must be in the same project");
        }
        if (neighbour.getRank() == null) {
            throw new ValidationException("Issue " + neighbour.getIssueKey() + " has no rank yet");
        }
        return neighbour.getRank();
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
