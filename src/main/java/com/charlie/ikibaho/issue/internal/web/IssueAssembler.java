package com.charlie.ikibaho.issue.internal.web;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.persistence.IssueTypeRepository;
import com.charlie.ikibaho.issue.internal.persistence.StatusRepository;
import com.charlie.ikibaho.issue.internal.web.dto.IssueListItem;
import com.charlie.ikibaho.issue.internal.web.dto.IssueResponse;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class IssueAssembler {
    private final UserService users;
    private final StatusRepository statuses;
    private final IssueTypeRepository types;

    IssueAssembler(UserService users, StatusRepository statuses, IssueTypeRepository types) {
        this.users = users;
        this.statuses = statuses;
        this.types = types;
    }

    private static IssueResponse.UserRef userRef(UserSummary u) {
        return u == null ? null : new IssueResponse.UserRef(u.id(), u.displayName(), u.avatarUrl());
    }

    IssueResponse toResponse(Issue issue) {
        Map<UUID, UserSummary> people = loadUsers(
                Stream.of(issue.getReporterId(), issue.getAssigneeId()));
        var status = statuses.findById(issue.getStatusId()).orElseThrow();
        var type = types.findById(issue.getTypeId()).orElseThrow();

        return new IssueResponse(
                issue.getId(), issue.getIssueKey(), issue.getProjectId(),
                issue.getSummary(), issue.getDescription(), issue.getPriority().name(),
                type.getId(), type.getName(),
                status.getId(), status.getName(), status.getCategory().name(),
                userRef(people.get(issue.getReporterId())),
                userRef(people.get(issue.getAssigneeId())),
                issue.getParentId(), issue.getStoryPoints(), issue.getDueDate(),
                issue.getCustomFields(), issue.getVersion(),
                issue.getCreatedAt(), issue.getUpdatedAt());
    }

    /**
     * One batch lookup for the whole page -- never findById per row.
     */
    List<IssueListItem> toListItems(List<IssueQueries.IssueRow> rows) {
        Map<UUID, UserSummary> people = loadUsers(
                rows.stream().flatMap(r -> Stream.of(r.reporterId(), r.assigneeId())));

        return rows.stream().map(r -> new IssueListItem(
                r.id(), r.issueKey(), r.summary(), r.priority(),
                r.typeId(), r.issueType(),
                r.statusId(), r.status(), r.statusCategory(),
                userRef(people.get(r.reporterId())),
                userRef(people.get(r.assigneeId())),
                r.storyPoints(), r.createdAt(), r.version())).toList();
    }

    private Map<UUID, UserSummary> loadUsers(Stream<UUID> ids) {
        Set<UUID> distinct = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        return users.findAllById(distinct).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }
}
