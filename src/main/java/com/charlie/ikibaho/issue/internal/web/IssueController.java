package com.charlie.ikibaho.issue.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.application.IssueRankingService;
import com.charlie.ikibaho.issue.internal.application.IssueTransitionService;
import com.charlie.ikibaho.issue.internal.domain.LinkType;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.web.dto.*;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.Cursor;
import com.charlie.ikibaho.platform.web.PageResponse;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.workflow.AvailableTransition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
@Validated
public class IssueController {
    private final IssueService issues;
    private final IssueTransitionService transitionService;
    private final IssueRankingService ranking;
    private final IssueQueries queries;
    private final IssueAssembler assembler;
    private final PermissionService permissions;
    private final CurrentUser currentUser;

    IssueController(IssueService issues, IssueTransitionService transitionService,
                    IssueRankingService ranking,
                    IssueQueries queries, IssueAssembler assembler,
                    PermissionService permissions, CurrentUser currentUser) {
        this.issues = issues;
        this.transitionService = transitionService;
        this.ranking = ranking;
        this.queries = queries;
        this.assembler = assembler;
        this.permissions = permissions;
        this.currentUser = currentUser;
    }

    @PostMapping("/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    IssueResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateIssueRequest req) {
        var cmd = new CreateIssueCommand(req.typeId(), req.summary(), req.description(),
                req.priority(), req.assigneeId(), req.parentId(), req.storyPoints(),
                req.dueDate(), req.customFields());
        return assembler.toResponse(issues.create(projectId, currentUser.requireId(), cmd));
    }

    @GetMapping("/issues/{issueId}")
    IssueResponse get(@PathVariable UUID issueId) {
        return assembler.toResponse(issues.getForUser(issueId, currentUser.requireId()));
    }

    @PatchMapping("/issues/{issueId}")
    IssueResponse update(@PathVariable UUID issueId, @Valid @RequestBody UpdateIssueRequest req) {
        var cmd = new UpdateIssueCommand(req.version(), req.summary(), req.description(),
                req.priority(), req.typeId(), req.storyPoints(), req.dueDate(), req.customFields());
        return assembler.toResponse(issues.update(issueId, currentUser.requireId(), cmd));
    }

    @PutMapping("/issues/{issueId}/assignee")
    IssueResponse assign(@PathVariable UUID issueId, @RequestBody AssignRequest req) {
        return assembler.toResponse(
                issues.assign(issueId, currentUser.requireId(), req.assigneeId()));
    }

    @DeleteMapping("/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID issueId) {
        issues.delete(issueId, currentUser.requireId());
    }

    /**
     * Cross-project list, filtered to browsable projects in SQL.
     */
    @GetMapping("/issues")
    PageResponse<IssueListItem> list(@RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        Set<UUID> projectIds = permissions.browsableProjectIds(currentUser.requireId());
        if (projectIds.isEmpty()) {
            return PageResponse.empty();
        }
        Cursor c = Cursor.decode(cursor);
        var rows = queries.list(projectIds,
                c == null ? null : c.createdAt(),
                c == null ? null : c.id(),
                limit);
        return page(rows, limit);
    }

    /**
     * Project backlog / issue list. Filters are optional and combine with AND.
     * Permission is checked in the service, so an unbrowsable project yields 404.
     */
    @GetMapping("/projects/{projectId}/issues")
    PageResponse<IssueListItem> listByProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID statusId,
            @RequestParam(required = false) String statusCategory,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {

        Cursor c = Cursor.decode(cursor);
        var filter = new IssueService.IssueFilter(assigneeId, statusId, statusCategory, typeId, search);
        var rows = issues.listForProject(projectId, currentUser.requireId(), filter,
                c == null ? null : c.createdAt(),
                c == null ? null : c.id(),
                limit);
        return page(rows, limit);
    }

    private PageResponse<IssueListItem> page(List<IssueQueries.IssueRow> rows, int limit) {
        String next = rows.isEmpty() ? null
                : new Cursor(rows.getLast().createdAt(), rows.getLast().id()).encode();
        return PageResponse.of(assembler.toListItems(rows), limit, next);
    }

    @PostMapping("/issues/{issueId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    void link(@PathVariable UUID issueId, @Valid @RequestBody LinkRequest req) {
        issues.link(issueId, currentUser.requireId(), req.targetId(), req.linkType());
    }

    @GetMapping("/issues/{issueId}/transitions")
    List<AvailableTransition> transitions(@PathVariable UUID issueId) {
        return transitionService.availableTransitions(issueId, currentUser.requireId());
    }

    @PostMapping("/issues/{issueId}/transitions")
    IssueResponse transition(@PathVariable UUID issueId,
                             @Valid @RequestBody TransitionRequest req) {
        return assembler.toResponse(
                transitionService.transition(issueId, currentUser.requireId(), req.transitionId()));
    }

    /**
     * Reorder, as a drop reports it: the two cards the dragged one landed between.
     * Both null means the list had nothing else in it.
     */
    @PutMapping("/issues/{issueId}/rank")
    IssueResponse rank(@PathVariable UUID issueId, @RequestBody RankRequest req) {
        return assembler.toResponse(ranking.moveBetween(
                issueId, currentUser.requireId(), req.afterId(), req.beforeId()));
    }

    record TransitionRequest(@NotNull UUID transitionId) {
    }

    record RankRequest(UUID afterId, UUID beforeId) {
    }

    record AssignRequest(UUID assigneeId) {
    }

    record LinkRequest(@NotNull UUID targetId, @NotNull LinkType linkType) {
    }
}
