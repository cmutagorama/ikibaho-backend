package com.charlie.ikibaho.board.internal.application;

import com.charlie.ikibaho.board.BacklogView;
import com.charlie.ikibaho.board.BoardService;
import com.charlie.ikibaho.board.BoardView;
import com.charlie.ikibaho.board.SprintResponse;
import com.charlie.ikibaho.board.internal.domain.Sprint;
import com.charlie.ikibaho.board.internal.domain.SprintState;
import com.charlie.ikibaho.board.internal.persistence.SprintIssueRepository;
import com.charlie.ikibaho.board.internal.persistence.SprintRepository;
import com.charlie.ikibaho.issue.BoardIssue;
import com.charlie.ikibaho.issue.IssueLookup;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.project.ProjectSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Assembles the board and backlog read models.
 * <p>
 * All of the issue data arrives in one call to the issue module's read model and
 * is grouped in memory. Querying per column would be one round trip per status
 * for a page that always renders every column at once.
 */
@Service
@Transactional(readOnly = true)
public class BoardServiceImpl implements BoardService {
    private final SprintRepository sprints;
    private final SprintIssueRepository membership;
    private final PermissionService permissions;
    private final ProjectService projects;
    private final IssueLookup issues;

    BoardServiceImpl(SprintRepository sprints, SprintIssueRepository membership,
                     PermissionService permissions, ProjectService projects, IssueLookup issues) {
        this.sprints = sprints;
        this.membership = membership;
        this.permissions = permissions;
        this.projects = projects;
        this.issues = issues;
    }

    /**
     * Unestimated issues count as zero rather than blocking the total.
     */
    private static BigDecimal sumPoints(List<BoardIssue> issues) {
        return issues.stream()
                .map(BoardIssue::storyPoints)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BoardView board(UUID projectId, UUID actorId) {
        permissions.require(actorId, projectId, Permission.BROWSE_PROJECT);
        UUID organizationId = organizationOf(projectId);

        Sprint active = sprints.findByProjectIdAndState(projectId, SprintState.ACTIVE).orElse(null);

        // No active sprint still returns the columns. An empty board with its
        // headers reads as "nothing is running"; an empty response reads as broken.
        Set<UUID> inSprint = active == null
                ? Set.of()
                : new HashSet<>(membership.findIssueIds(active.getId()));

        Map<UUID, List<BoardIssue>> byStatus = new LinkedHashMap<>();
        for (BoardIssue issue : issues.findRankedByProject(projectId)) {
            if (inSprint.contains(issue.id())) {
                byStatus.computeIfAbsent(issue.statusId(), k -> new ArrayList<>()).add(issue);
            }
        }

        List<BoardView.Column> columns = new ArrayList<>();
        int total = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (IssueLookup.StatusView status : issues.statusesFor(organizationId)) {
            List<BoardIssue> column = byStatus.getOrDefault(status.id(), List.of());
            BigDecimal points = sumPoints(column);
            columns.add(new BoardView.Column(status.id(), status.name(), status.category(),
                    column, column.size(), points));
            total += column.size();
            totalPoints = totalPoints.add(points);
        }

        SprintResponse sprint = active == null
                ? null
                : SprintService.toResponse(active, inSprint.size());
        return new BoardView(projectId, sprint, columns, total, totalPoints);
    }

    @Override
    public BacklogView backlog(UUID projectId, UUID actorId) {
        permissions.require(actorId, projectId, Permission.BROWSE_PROJECT);

        List<BoardIssue> ranked = issues.findRankedByProject(projectId);
        Set<UUID> committed = new HashSet<>(membership.findIssueIdsInOpenSprints(projectId));

        List<BacklogView.PlannedSprint> planned = new ArrayList<>();
        for (Sprint sprint : sprints.findByProjectIdAndStateNot(projectId, SprintState.COMPLETED)) {
            Set<UUID> ids = new HashSet<>(membership.findIssueIds(sprint.getId()));
            // Filtered from the ranked list rather than fetched separately, so a
            // sprint's issues keep the same order they have everywhere else.
            List<BoardIssue> contents = ranked.stream().filter(i -> ids.contains(i.id())).toList();
            planned.add(new BacklogView.PlannedSprint(
                    SprintService.toResponse(sprint, contents.size()),
                    contents, sumPoints(contents)));
        }

        // A backlog is work still to do. Finished issues drop out of it -- otherwise
        // completing a sprint would hand every issue it just closed straight back
        // to the planning screen, which is the opposite of what completing means.
        List<BoardIssue> backlog = ranked.stream()
                .filter(issue -> !committed.contains(issue.id()))
                .filter(issue -> !"DONE".equals(issue.statusCategory()))
                .toList();

        return new BacklogView(planned, backlog, backlog.size(), sumPoints(backlog));
    }

    private UUID organizationOf(UUID projectId) {
        return projects.findById(projectId)
                .map(ProjectSummary::organizationId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
    }
}
