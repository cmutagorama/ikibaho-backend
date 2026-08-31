package com.charlie.ikibaho.board;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.board.internal.application.SprintService;
import com.charlie.ikibaho.issue.BoardIssue;
import com.charlie.ikibaho.issue.internal.application.IssueRankingService;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.application.IssueTransitionService;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.support.TestFixtures;
import com.charlie.ikibaho.workflow.AvailableTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    IssueService issues;
    @Autowired
    IssueRankingService ranking;
    @Autowired
    IssueTransitionService transitions;
    @Autowired
    SprintService sprints;
    @Autowired
    BoardService boards;
    @Autowired
    TestFixtures fixtures;

    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID taskTypeId;

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
    }

    // ---------------------------------------------------------------- ranking

    @Test
    void newIssuesLandAtTheBottomOfTheBacklogInOrder() {
        Issue first = create("First");
        Issue second = create("Second");
        Issue third = create("Third");

        // Filing a bug must not reprioritise anyone's backlog, so new work goes last.
        assertThat(backlogKeys()).containsExactly(
                first.getIssueKey(), second.getIssueKey(), third.getIssueKey());
        assertThat(first.getRank()).isLessThan(second.getRank());
        assertThat(second.getRank()).isLessThan(third.getRank());
    }

    @Test
    void draggingAnIssueBetweenTwoOthersReordersOnlyThatIssue() {
        Issue a = create("A");
        Issue b = create("B");
        Issue c = create("C");
        String untouchedRankOfA = a.getRank();

        // Drop C between A and B.
        ranking.moveBetween(c.getId(), adminId, a.getId(), b.getId());

        assertThat(backlogKeys()).containsExactly(
                a.getIssueKey(), c.getIssueKey(), b.getIssueKey());
        // The point of rank strings: the neighbours' rows are never rewritten.
        assertThat(a.getRank()).isEqualTo(untouchedRankOfA);
    }

    @Test
    void movingToTheTopAndBottomUsesTheNullNeighbour() {
        Issue a = create("A");
        Issue b = create("B");
        Issue c = create("C");

        ranking.moveBetween(c.getId(), adminId, null, a.getId());
        assertThat(backlogKeys()).startsWith(c.getIssueKey());

        ranking.moveBetween(c.getId(), adminId, b.getId(), null);
        assertThat(backlogKeys()).endsWith(c.getIssueKey());
    }

    @Test
    void refusesToRankAnIssueAgainstItself() {
        Issue a = create("A");
        Issue b = create("B");

        assertThatThrownBy(() -> ranking.moveBetween(a.getId(), adminId, a.getId(), b.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("relative to itself");
    }

    @Test
    void refusesNeighboursThatAreNoLongerInThatOrder() {
        Issue a = create("A");
        Issue b = create("B");
        Issue c = create("C");

        // b before a is backwards -- the board the client was looking at is stale.
        assertThatThrownBy(() -> ranking.moveBetween(c.getId(), adminId, b.getId(), a.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("refresh the board");
    }

    // ----------------------------------------------------------------- sprint

    @Test
    void runsASprintFromPlanningToCompletion() {
        Issue done = create("Will finish");
        Issue unfinished = create("Will not finish");

        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", "Ship it", null, null);
        assertThat(sprint.state()).isEqualTo("FUTURE");

        sprints.addIssues(sprint.id(), adminId, Set.of(done.getId(), unfinished.getId()));
        assertThat(sprints.start(sprint.id(), adminId).state()).isEqualTo("ACTIVE");

        moveToDone(done.getId());

        SprintService.CompletionReport report = sprints.complete(sprint.id(), adminId);

        assertThat(report.sprint().state()).isEqualTo("COMPLETED");
        assertThat(report.completedIssues()).isEqualTo(1);

        // Re-read rather than trusting the returned object. The first version of
        // this assertion passed against a detached entity whose completion was
        // never actually written -- the response said COMPLETED, the row said
        // ACTIVE.
        assertThat(sprints.listForProject(projectId, adminId))
                .filteredOn(s -> s.id().equals(sprint.id()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.state()).isEqualTo("COMPLETED");
                    assertThat(s.completedAt()).isNotNull();
                });
        // Unfinished work returns to the backlog rather than being counted as done.
        assertThat(report.movedToBacklog()).containsExactly(unfinished.getId());
        assertThat(backlogKeys()).contains(unfinished.getIssueKey());
        assertThat(backlogKeys()).doesNotContain(done.getIssueKey());
    }

    @Test
    void allowsOnlyOneActiveSprintPerProject() {
        Issue a = create("A");
        Issue b = create("B");

        SprintResponse first = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        sprints.addIssues(first.id(), adminId, Set.of(a.getId()));
        sprints.start(first.id(), adminId);

        SprintResponse second = sprints.create(projectId, adminId, "Sprint 2", null, null, null);
        sprints.addIssues(second.id(), adminId, Set.of(b.getId()));

        assertThatThrownBy(() -> sprints.start(second.id(), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void refusesToStartAnEmptySprint() {
        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", null, null, null);

        assertThatThrownBy(() -> sprints.start(sprint.id(), adminId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one issue");
    }

    @Test
    void movesAnIssueBetweenSprintsRatherThanCommittingItTwice() {
        Issue issue = create("Contested");

        SprintResponse first = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        SprintResponse second = sprints.create(projectId, adminId, "Sprint 2", null, null, null);

        sprints.addIssues(first.id(), adminId, Set.of(issue.getId()));
        sprints.addIssues(second.id(), adminId, Set.of(issue.getId()));

        BacklogView backlog = boards.backlog(projectId, adminId);
        assertThat(contentsOf(backlog, first.id())).isEmpty();
        assertThat(contentsOf(backlog, second.id())).hasSize(1);
    }

    @Test
    void refusesToChangeTheScopeOfACompletedSprint() {
        Issue a = create("A");
        Issue b = create("B");

        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        sprints.addIssues(sprint.id(), adminId, Set.of(a.getId()));
        sprints.start(sprint.id(), adminId);
        sprints.complete(sprint.id(), adminId);

        assertThatThrownBy(() -> sprints.addIssues(sprint.id(), adminId, Set.of(b.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("scope is fixed");
    }

    @Test
    void keepsACompletedSprintAsAHistoricalRecord() {
        Issue a = create("A");
        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        sprints.addIssues(sprint.id(), adminId, Set.of(a.getId()));
        sprints.start(sprint.id(), adminId);
        sprints.complete(sprint.id(), adminId);

        assertThatThrownBy(() -> sprints.delete(sprint.id(), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("historical record");
    }

    // ------------------------------------------------------------------ board

    @Test
    void boardShowsOnlyTheActiveSprintAndKeepsEveryColumn() {
        Issue inSprint = create("Committed");
        create("Still in the backlog");

        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        sprints.addIssues(sprint.id(), adminId, Set.of(inSprint.getId()));
        sprints.start(sprint.id(), adminId);

        BoardView board = boards.board(projectId, adminId);

        assertThat(board.sprint().name()).isEqualTo("Sprint 1");
        assertThat(board.totalIssues()).isEqualTo(1);
        // Three default statuses; empty ones still need a header to drop cards into.
        assertThat(board.columns()).hasSize(3);
        assertThat(board.columns()).extracting(BoardView.Column::name)
                .containsExactly("To Do", "In Progress", "Done");
        assertThat(allIssues(board)).extracting(BoardIssue::issueKey)
                .containsExactly(inSprint.getIssueKey());
    }

    @Test
    void boardWithoutAnActiveSprintStillRendersItsColumns() {
        create("Unplanned");

        BoardView board = boards.board(projectId, adminId);

        assertThat(board.sprint()).isNull();
        assertThat(board.columns()).hasSize(3);
        assertThat(board.totalIssues()).isZero();
    }

    @Test
    void backlogExcludesWorkCommittedToAnOpenSprint() {
        Issue committed = create("Committed");
        Issue free = create("Free");

        SprintResponse sprint = sprints.create(projectId, adminId, "Sprint 1", null, null, null);
        sprints.addIssues(sprint.id(), adminId, Set.of(committed.getId()));

        BacklogView backlog = boards.backlog(projectId, adminId);

        assertThat(backlog.backlog()).extracting(BoardIssue::issueKey)
                .containsExactly(free.getIssueKey());
        // A future sprint is shown with its contents so it can be planned against.
        assertThat(backlog.sprints()).hasSize(1);
        assertThat(contentsOf(backlog, sprint.id())).extracting(BoardIssue::issueKey)
                .containsExactly(committed.getIssueKey());
    }

    // ---------------------------------------------------------------- helpers

    private Issue create(String summary) {
        return issues.create(projectId, adminId, new CreateIssueCommand(
                taskTypeId, summary, null, null, null, null, null, null, null));
    }

    private void moveToDone(UUID issueId) {
        for (int step = 0; step < 5; step++) {
            List<AvailableTransition> available = transitions.availableTransitions(issueId, adminId);
            AvailableTransition next = available.stream()
                    .filter(t -> "Done".equals(t.toStatusName()))
                    .findFirst()
                    .orElse(available.isEmpty() ? null : available.getFirst());
            if (next == null) {
                break;
            }
            transitions.transition(issueId, adminId, next.id());
            if (isDone(issueId)) {
                return;
            }
        }
        throw new IllegalStateException("Could not walk the issue to a Done status");
    }

    private boolean isDone(UUID issueId) {
        return boards.backlog(projectId, adminId).sprints().stream()
                .flatMap(s -> s.issues().stream())
                .anyMatch(i -> i.id().equals(issueId) && "DONE".equals(i.statusCategory()));
    }

    private List<String> backlogKeys() {
        return boards.backlog(projectId, adminId).backlog().stream()
                .map(BoardIssue::issueKey)
                .toList();
    }

    private static List<BoardIssue> contentsOf(BacklogView backlog, UUID sprintId) {
        return backlog.sprints().stream()
                .filter(s -> s.sprint().id().equals(sprintId))
                .findFirst()
                .map(BacklogView.PlannedSprint::issues)
                .orElseThrow();
    }

    private static List<BoardIssue> allIssues(BoardView board) {
        return board.columns().stream().flatMap(c -> c.issues().stream()).toList();
    }
}
