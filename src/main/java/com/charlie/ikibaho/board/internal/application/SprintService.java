package com.charlie.ikibaho.board.internal.application;

import com.charlie.ikibaho.board.internal.domain.Sprint;
import com.charlie.ikibaho.board.internal.domain.SprintIssue;
import com.charlie.ikibaho.board.internal.domain.SprintState;
import com.charlie.ikibaho.board.internal.persistence.SprintIssueRepository;
import com.charlie.ikibaho.board.internal.persistence.SprintRepository;
import com.charlie.ikibaho.board.SprintResponse;
import com.charlie.ikibaho.issue.BoardIssue;
import com.charlie.ikibaho.issue.IssueLookup;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The sprint lifecycle, and what happens to work when a sprint ends.
 */
@Service
@Transactional(readOnly = true)
public class SprintService {
    private final SprintRepository sprints;
    private final SprintIssueRepository membership;
    private final PermissionService permissions;
    private final IssueLookup issues;

    SprintService(SprintRepository sprints, SprintIssueRepository membership,
                  PermissionService permissions, IssueLookup issues) {
        this.sprints = sprints;
        this.membership = membership;
        this.permissions = permissions;
        this.issues = issues;
    }

    @Transactional
    public SprintResponse create(UUID projectId, UUID actorId, String name, String goal,
                                 Instant plannedStart, Instant plannedEnd) {
        permissions.require(actorId, projectId, Permission.MANAGE_SPRINT);
        Sprint sprint = sprints.save(new Sprint(projectId, name.trim(), goal, plannedStart, plannedEnd));
        return toResponse(sprint, 0);
    }

    public List<SprintResponse> listForProject(UUID projectId, UUID actorId) {
        permissions.require(actorId, projectId, Permission.BROWSE_PROJECT);
        return sprints.findByProjectIdOrderByStateAscCreatedAtAsc(projectId).stream()
                .map(s -> toResponse(s, membership.findIssueIds(s.getId()).size()))
                .toList();
    }

    /**
     * Start a sprint.
     *
     * One active sprint per project: a team running two at once has no single
     * answer to "what are we working on now", which is the only question a board
     * exists to answer. A partial unique index enforces this in the database too,
     * so a concurrent start cannot slip past the check.
     */
    @Transactional
    public SprintResponse start(UUID sprintId, UUID actorId) {
        Sprint sprint = load(sprintId);
        permissions.require(actorId, sprint.getProjectId(), Permission.MANAGE_SPRINT);

        sprints.findByProjectIdAndState(sprint.getProjectId(), SprintState.ACTIVE)
                .ifPresent(active -> {
                    throw new ConflictException(
                            "Sprint " + active.getName() + " is already active; complete it first");
                });

        if (membership.findIssueIds(sprintId).isEmpty()) {
            throw new ValidationException("Add at least one issue before starting the sprint");
        }

        sprint.start(Instant.now());
        try {
            sprints.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Another sprint was started at the same time");
        }
        return toResponse(sprint, membership.findIssueIds(sprintId).size());
    }

    /**
     * Complete a sprint, returning unfinished work to the backlog.
     *
     * Anything not in a DONE status leaves the sprint rather than being marked
     * finished: a sprint report that silently absorbed incomplete work would
     * overstate velocity, and velocity that flatters is worse than none.
     *
     * @return the issues that were carried out of the sprint
     */
    @Transactional
    public CompletionReport complete(UUID sprintId, UUID actorId) {
        Sprint sprint = load(sprintId);
        permissions.require(actorId, sprint.getProjectId(), Permission.MANAGE_SPRINT);

        List<UUID> issueIds = membership.findIssueIds(sprintId);
        Map<UUID, BoardIssue> byId = issues.findRankedByProject(sprint.getProjectId()).stream()
                .collect(Collectors.toMap(BoardIssue::id, Function.identity()));

        List<UUID> incomplete = issueIds.stream()
                .filter(id -> {
                    BoardIssue issue = byId.get(id);
                    // An issue deleted mid-sprint has nothing to carry over.
                    return issue != null && !"DONE".equals(issue.statusCategory());
                })
                .toList();

        if (!incomplete.isEmpty()) {
            membership.removeAll(sprintId, incomplete);
        }
        sprint.complete(Instant.now());

        int completed = issueIds.size() - incomplete.size();
        return new CompletionReport(toResponse(sprint, completed), completed, incomplete);
    }

    /**
     * Add issues to a sprint's scope.
     *
     * Issues already in another open sprint are moved rather than duplicated --
     * one issue cannot be committed to two sprints at once.
     */
    @Transactional
    public int addIssues(UUID sprintId, UUID actorId, Set<UUID> issueIds) {
        Sprint sprint = load(sprintId);
        permissions.require(actorId, sprint.getProjectId(), Permission.MANAGE_SPRINT);
        sprint.requireOpen();

        Set<UUID> inProject = issues.findRankedByProject(sprint.getProjectId()).stream()
                .map(BoardIssue::id)
                .collect(Collectors.toSet());

        Set<UUID> foreign = new HashSet<>(issueIds);
        foreign.removeAll(inProject);
        if (!foreign.isEmpty()) {
            throw new ValidationException(
                    "These issues are not in this project: " + foreign);
        }

        // Clear any existing commitment before making a new one.
        for (Sprint other : sprints.findByProjectIdAndStateNot(sprint.getProjectId(), SprintState.COMPLETED)) {
            if (!other.getId().equals(sprintId)) {
                membership.removeAll(other.getId(), issueIds);
            }
        }

        Set<UUID> already = new HashSet<>(membership.findIssueIds(sprintId));
        int added = 0;
        for (UUID issueId : issueIds) {
            if (already.add(issueId)) {
                membership.save(new SprintIssue(sprintId, issueId));
                added++;
            }
        }
        return added;
    }

    @Transactional
    public int removeIssues(UUID sprintId, UUID actorId, Set<UUID> issueIds) {
        Sprint sprint = load(sprintId);
        permissions.require(actorId, sprint.getProjectId(), Permission.MANAGE_SPRINT);
        sprint.requireOpen();

        return issueIds.isEmpty() ? 0 : membership.removeAll(sprintId, issueIds);
    }

    @Transactional
    public void delete(UUID sprintId, UUID actorId) {
        Sprint sprint = load(sprintId);
        permissions.require(actorId, sprint.getProjectId(), Permission.MANAGE_SPRINT);

        if (sprint.isCompleted()) {
            throw new ConflictException("A completed sprint is a historical record and cannot be deleted");
        }
        // Membership goes first: the issues themselves are untouched and return to
        // the backlog, which is the only sane reading of deleting a plan.
        membership.removeAllInSprint(sprintId);
        sprints.delete(sprint);
    }

    Sprint load(UUID sprintId) {
        return sprints.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint", sprintId));
    }

    static SprintResponse toResponse(Sprint s, int issueCount) {
        return new SprintResponse(s.getId(), s.getProjectId(), s.getName(), s.getGoal(),
                s.getState().name(), s.getPlannedStart(), s.getPlannedEnd(),
                s.getStartedAt(), s.getCompletedAt(), issueCount);
    }

    /** What completing a sprint did, so the client can say so rather than guess. */
    public record CompletionReport(SprintResponse sprint, int completedIssues,
                                   List<UUID> movedToBacklog) {
    }
}
