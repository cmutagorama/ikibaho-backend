package com.charlie.ikibaho.issue.internal.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IssueQueries {
    private static final String SELECT = """
            SELECT i.id, i.issue_key, i.summary, i.priority,
                   i.status_id, s.name AS status, s.category AS status_category,
                   i.type_id, t.name AS issue_type,
                   i.assignee_id, i.reporter_id,
                   i.story_points, i.created_at, i.version
            FROM issue i
            JOIN status s     ON s.id = i.status_id
            JOIN issue_type t ON t.id = i.type_id
            """;
    private final JdbcClient jdbc;

    IssueQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The bottom of a project's backlog, or null when it is empty.
     * <p>
     * Reads the single greatest rank rather than loading the list to find its
     * last element -- the index makes this O(1) whatever the backlog's size.
     */
    public String lowestRank(UUID projectId) {
        return jdbc.sql("""
                        SELECT rank FROM issue
                        WHERE project_id = :projectId AND NOT deleted AND rank IS NOT NULL
                        ORDER BY rank DESC
                        LIMIT 1
                        """)
                .param("projectId", projectId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /**
     * Every issue on a project's board, in rank order.
     * <p>
     * Published to the board module as a read model. Board groups these into
     * columns; it does not get to reach into issue tables to do it.
     */
    public List<RankedIssueRow> rankedByProject(UUID projectId) {
        return jdbc.sql("""
                        SELECT i.id, i.issue_key, i.summary, i.priority,
                               i.status_id, s.name AS status, s.category AS status_category,
                               i.type_id, t.name AS issue_type,
                               i.assignee_id, i.parent_id, i.story_points, i.rank
                        FROM issue i
                        JOIN status s     ON s.id = i.status_id
                        JOIN issue_type t ON t.id = i.type_id
                        WHERE i.project_id = :projectId AND NOT i.deleted
                        ORDER BY i.rank NULLS LAST, i.created_at
                        """)
                .param("projectId", projectId)
                .query(RankedIssueRow.class)
                .list();
    }

    /**
     * Keyset pagination. The (created_at, id) row comparison walks
     * idx_issue_project_created directly -- OFFSET would scan and discard.
     */
    public List<IssueRow> list(Collection<UUID> projectIds, Instant cursorCreatedAt,
                               UUID cursorId, int limit) {
        OffsetDateTime cursorTs = cursorCreatedAt == null ? null : cursorCreatedAt.atOffset(ZoneOffset.UTC);
        return jdbc.sql(SELECT + """
                        WHERE i.project_id = ANY(:projectIds)
                          AND i.deleted = false
                          AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL
                               OR (i.created_at, i.id) < (CAST(:cursorCreatedAt AS timestamptz),
                                                          CAST(:cursorId AS uuid)))
                        ORDER BY i.created_at DESC, i.id DESC
                        LIMIT :limit
                        """)
                .param("projectIds", projectIds.toArray(UUID[]::new))
                .param("cursorCreatedAt", cursorTs, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("cursorId", cursorId, Types.OTHER)
                .param("limit", limit)
                .query(IssueRow.class)
                .list();
    }

    /**
     * Project-scoped list with optional filters, same keyset pagination as list().
     * <p>
     * Filters use the "IS NULL OR matches" shape so one prepared statement serves every
     * combination -- string-concatenating a WHERE clause per combination would defeat
     * plan reuse and is the usual doorway to injection.
     */
    public List<IssueRow> listByProject(UUID projectId, UUID assigneeId, UUID statusId,
                                        String statusCategory, UUID typeId, String search,
                                        Instant cursorCreatedAt, UUID cursorId, int limit) {
        OffsetDateTime cursorTs = cursorCreatedAt == null ? null : cursorCreatedAt.atOffset(ZoneOffset.UTC);
        return jdbc.sql(SELECT + """
                        WHERE i.project_id = :projectId
                          AND i.deleted = false
                          AND (CAST(:assigneeId AS uuid) IS NULL OR i.assignee_id = CAST(:assigneeId AS uuid))
                          AND (CAST(:statusId   AS uuid) IS NULL OR i.status_id   = CAST(:statusId   AS uuid))
                          AND (CAST(:typeId     AS uuid) IS NULL OR i.type_id     = CAST(:typeId     AS uuid))
                          AND (CAST(:statusCategory AS text) IS NULL OR s.category = CAST(:statusCategory AS text))
                          AND (CAST(:search AS text) IS NULL
                               OR i.summary ILIKE '%' || CAST(:search AS text) || '%'
                               OR i.issue_key ILIKE CAST(:search AS text) || '%')
                          AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL
                               OR (i.created_at, i.id) < (CAST(:cursorCreatedAt AS timestamptz),
                                                          CAST(:cursorId AS uuid)))
                        ORDER BY i.created_at DESC, i.id DESC
                        LIMIT :limit
                        """)
                .param("projectId", projectId)
                .param("assigneeId", assigneeId, Types.OTHER)
                .param("statusId", statusId, Types.OTHER)
                .param("typeId", typeId, Types.OTHER)
                .param("statusCategory", statusCategory, Types.VARCHAR)
                .param("search", search, Types.VARCHAR)
                .param("cursorCreatedAt", cursorTs, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("cursorId", cursorId, Types.OTHER)
                .param("limit", limit)
                .query(IssueRow.class)
                .list();
    }

    /**
     * A whole issue plus its comment text, for indexing.
     * <p>
     * The comments are aggregated in SQL rather than fetched as rows: the indexer
     * only ever concatenates them, and a hundred comment rows crossing the wire to
     * be joined with a space is waste on the write path of every edit.
     */
    public Optional<IndexRow> indexRow(UUID issueId) {
        return jdbc.sql("""
                        SELECT i.id                AS issue_id,
                               i.organization_id, i.project_id,
                               p.key               AS project_key,
                               i.issue_key, i.issue_number,
                               i.summary, i.description,
                               i.type_id, t.name   AS type_name,
                               i.status_id, s.name AS status_name, s.category AS status_category,
                               i.priority,
                               i.assignee_id, i.reporter_id, i.parent_id,
                               i.story_points, i.due_date, i.rank,
                               (SELECT string_agg(c.body, ' ')
                                  FROM comment c
                                 WHERE c.issue_id = i.id AND NOT c.deleted) AS comment_text,
                               i.created_at, i.updated_at
                          FROM issue i
                          JOIN project p     ON p.id = i.project_id
                          JOIN status s      ON s.id = i.status_id
                          JOIN issue_type t  ON t.id = i.type_id
                         WHERE i.id = :issueId AND NOT i.deleted
                        """)
                .param("issueId", issueId)
                .query(IndexRow.class)
                .optional();
    }

    public int countOpenSubtasks(UUID parentId) {
        return jdbc.sql("""
                        SELECT count(*)
                        FROM issue i
                        JOIN status s ON s.id = i.status_id
                        WHERE i.parent_id = :parentId
                          AND i.deleted = false
                          AND s.category <> 'DONE'
                        """)
                .param("parentId", parentId)
                .query(Integer.class)
                .single();
    }

    public record RankedIssueRow(
            UUID id, String issueKey, String summary, String priority,
            UUID statusId, String status, String statusCategory,
            UUID typeId, String issueType,
            UUID assigneeId, UUID parentId, BigDecimal storyPoints, String rank) {
    }

    public record IssueRow(
            UUID id, String issueKey, String summary, String priority,
            UUID statusId, String status, String statusCategory,
            UUID typeId, String issueType,
            UUID assigneeId, UUID reporterId,
            BigDecimal storyPoints, Instant createdAt, long version) {
    }

    public record IndexRow(
            UUID issueId, UUID organizationId, UUID projectId, String projectKey,
            String issueKey, long issueNumber, String summary, String description,
            UUID typeId, String typeName,
            UUID statusId, String statusName, String statusCategory,
            String priority, UUID assigneeId, UUID reporterId, UUID parentId,
            BigDecimal storyPoints, LocalDate dueDate, String rank,
            String commentText, Instant createdAt, Instant updatedAt) {
    }
}
