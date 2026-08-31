package com.charlie.ikibaho.issue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What board, search and notification consume.
 */
public interface IssueLookup {
    Optional<IssueSummary> findById(UUID issueId);

    Optional<IssueSummary> findByKey(String issueKey);

    List<IssueSummary> findAllById(Collection<UUID> ids);

    /**
     * Every issue in a project, in rank order, shaped for a board or backlog.
     * <p>
     * Deliberately unpaged: a board renders a whole project at once, and a
     * partial board is worse than a slow one -- a column missing its tail reads
     * as work that does not exist. Projects large enough for this to hurt want a
     * sprint filter, which is board's job to apply.
     */
    List<BoardIssue> findRankedByProject(UUID projectId);

    /**
     * The organization's statuses in workflow order -- the columns of a board.
     */
    List<StatusView> statusesFor(UUID organizationId);

    /**
     * One issue, shaped for an external index. Empty when the issue is gone --
     * which the indexer treats as "remove it", not as an error.
     */
    Optional<IssueIndexView> findForIndex(UUID issueId);

    record StatusView(UUID id, String name, String category, int position) {
    }
}
