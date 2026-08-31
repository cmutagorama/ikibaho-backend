package com.charlie.ikibaho.board.internal.web;

import com.charlie.ikibaho.board.BacklogView;
import com.charlie.ikibaho.board.BoardService;
import com.charlie.ikibaho.board.BoardView;
import com.charlie.ikibaho.board.SprintResponse;
import com.charlie.ikibaho.board.internal.application.SprintService;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
@Validated
class BoardController {
    private final BoardService boards;
    private final SprintService sprints;
    private final CurrentUser currentUser;

    BoardController(BoardService boards, SprintService sprints, CurrentUser currentUser) {
        this.boards = boards;
        this.sprints = sprints;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{projectId}/board")
    BoardView board(@PathVariable UUID projectId) {
        return boards.board(projectId, currentUser.requireId());
    }

    @GetMapping("/projects/{projectId}/backlog")
    BacklogView backlog(@PathVariable UUID projectId) {
        return boards.backlog(projectId, currentUser.requireId());
    }

    @GetMapping("/projects/{projectId}/sprints")
    List<SprintResponse> sprints(@PathVariable UUID projectId) {
        return sprints.listForProject(projectId, currentUser.requireId());
    }

    @PostMapping("/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    SprintResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateSprintRequest req) {
        return sprints.create(projectId, currentUser.requireId(), req.name(), req.goal(),
                req.plannedStart(), req.plannedEnd());
    }

    @PostMapping("/sprints/{sprintId}/start")
    SprintResponse start(@PathVariable UUID sprintId) {
        return sprints.start(sprintId, currentUser.requireId());
    }

    @PostMapping("/sprints/{sprintId}/complete")
    SprintService.CompletionReport complete(@PathVariable UUID sprintId) {
        return sprints.complete(sprintId, currentUser.requireId());
    }

    @PostMapping("/sprints/{sprintId}/issues")
    Map<String, Integer> addIssues(@PathVariable UUID sprintId,
                                   @Valid @RequestBody IssueIdsRequest req) {
        return Map.of("added", sprints.addIssues(sprintId, currentUser.requireId(), req.issueIds()));
    }

    @DeleteMapping("/sprints/{sprintId}/issues")
    Map<String, Integer> removeIssues(@PathVariable UUID sprintId,
                                      @Valid @RequestBody IssueIdsRequest req) {
        return Map.of("removed", sprints.removeIssues(sprintId, currentUser.requireId(), req.issueIds()));
    }

    @DeleteMapping("/sprints/{sprintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID sprintId) {
        sprints.delete(sprintId, currentUser.requireId());
    }

    record CreateSprintRequest(@NotBlank String name, String goal,
                               Instant plannedStart, Instant plannedEnd) {
    }

    record IssueIdsRequest(@NotEmpty Set<UUID> issueIds) {
    }
}
