package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.BoardIssue;
import com.charlie.ikibaho.issue.IssueIndexView;
import com.charlie.ikibaho.issue.IssueLookup;
import com.charlie.ikibaho.issue.IssueSummary;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.persistence.IssueQueries;
import com.charlie.ikibaho.issue.internal.persistence.IssueRepository;
import com.charlie.ikibaho.issue.internal.persistence.StatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IssueLookupImpl implements IssueLookup {
    private final IssueRepository issues;
    private final IssueQueries issueQueries;
    private final StatusRepository statuses;

    IssueLookupImpl(IssueRepository issues, IssueQueries issueQueries, StatusRepository statuses) {
        this.issues = issues;
        this.issueQueries = issueQueries;
        this.statuses = statuses;
    }

    /**
     * No permission checks here. This is the module SPI -- calling modules
     * (board, search, notification) are trusted and do their own filtering.
     */
    private static IssueSummary toSummary(Issue i) {
        return new IssueSummary(i.getId(), i.getProjectId(), i.getIssueKey(),
                i.getSummary(), i.getStatusId(), i.getAssigneeId(), i.getReporterId());
    }

    @Override
    public Optional<IssueSummary> findById(UUID issueId) {
        return issues.findById(issueId).map(IssueLookupImpl::toSummary);
    }

    @Override
    public Optional<IssueSummary> findByKey(String issueKey) {
        return issues.findByIssueKey(issueKey.toUpperCase()).map(IssueLookupImpl::toSummary);
    }

    @Override
    public List<IssueSummary> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return issues.findAllById(ids).stream().map(IssueLookupImpl::toSummary).toList();
    }

    @Override
    public List<BoardIssue> findRankedByProject(UUID projectId) {
        return issueQueries.rankedByProject(projectId).stream()
                .map(r -> new BoardIssue(r.id(), r.issueKey(), r.summary(), r.priority(),
                        r.statusId(), r.status(), r.statusCategory(), r.typeId(), r.issueType(),
                        r.assigneeId(), r.parentId(), r.storyPoints(), r.rank()))
                .toList();
    }

    @Override
    public List<StatusView> statusesFor(UUID organizationId) {
        return statuses.findByOrganizationIdOrderByPositionAsc(organizationId).stream()
                .map(s -> new StatusView(s.getId(), s.getName(), s.getCategory().name(), s.getPosition()))
                .toList();
    }

    @Override
    public Optional<IssueIndexView> findForIndex(UUID issueId) {
        return issueQueries.indexRow(issueId).map(r -> new IssueIndexView(r.issueId(), r.organizationId(), r.projectId(),
                r.projectKey(), r.issueKey(), r.issueNumber(), r.summary(), r.description(),
                r.typeId(), r.typeName(), r.statusId(), r.statusName(), r.statusCategory(),
                r.priority(), r.assigneeId(), r.reporterId(), r.parentId(),
                r.storyPoints(), r.dueDate(), r.rank(), r.commentText(),
                r.createdAt(), r.updatedAt()));
    }
}
