package com.charlie.ikibaho.search.internal.persistence;

import com.charlie.ikibaho.issue.IssueIndexView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Writes to the search index.
 * <p>
 * Plain JDBC rather than JPA: the row has a generated tsvector column that
 * Hibernate would have to be told to ignore on every write, and an upsert
 * expresses "make the index match the issue" far better than load-modify-save.
 */
@Repository
public class SearchIndexWriter {
    private final JdbcClient jdbc;

    SearchIndexWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert or replace the document for one issue.
     * <p>
     * An upsert, deliberately: the indexer is driven by at-least-once events, so
     * it must be safe to run twice for the same issue. Re-indexing is idempotent
     * by construction rather than by a dedupe key, because unlike an audit line a
     * document has no history to duplicate -- writing it again just writes the
     * same thing.
     */
    public void index(IssueIndexView view) {
        jdbc.sql("""
                        INSERT INTO issue_search_index
                            (issue_id, organization_id, project_id, project_key, issue_key,
                             issue_number, summary, description, type_id, type_name,
                             status_id, status_name, status_category, priority,
                             assignee_id, reporter_id, parent_id, story_points, due_date,
                             rank, comment_text, created_at, updated_at)
                        VALUES
                            (:issueId, :organizationId, :projectId, :projectKey, :issueKey,
                             :issueNumber, :summary, :description, :typeId, :typeName,
                             :statusId, :statusName, :statusCategory, :priority,
                             :assigneeId, :reporterId, :parentId, :storyPoints, :dueDate,
                             :rank, :commentText, :createdAt, :updatedAt)
                        ON CONFLICT (issue_id) DO UPDATE SET
                             project_id      = EXCLUDED.project_id,
                             project_key     = EXCLUDED.project_key,
                             issue_key       = EXCLUDED.issue_key,
                             summary         = EXCLUDED.summary,
                             description     = EXCLUDED.description,
                             type_id         = EXCLUDED.type_id,
                             type_name       = EXCLUDED.type_name,
                             status_id       = EXCLUDED.status_id,
                             status_name     = EXCLUDED.status_name,
                             status_category = EXCLUDED.status_category,
                             priority        = EXCLUDED.priority,
                             assignee_id     = EXCLUDED.assignee_id,
                             reporter_id     = EXCLUDED.reporter_id,
                             parent_id       = EXCLUDED.parent_id,
                             story_points    = EXCLUDED.story_points,
                             due_date        = EXCLUDED.due_date,
                             rank            = EXCLUDED.rank,
                             comment_text    = EXCLUDED.comment_text,
                             updated_at      = EXCLUDED.updated_at
                        """)
                .param("issueId", view.issueId())
                .param("organizationId", view.organizationId())
                .param("projectId", view.projectId())
                .param("projectKey", view.projectKey())
                .param("issueKey", view.issueKey())
                .param("issueNumber", view.issueNumber())
                .param("summary", view.summary())
                .param("description", view.description())
                .param("typeId", view.typeId())
                .param("typeName", view.typeName())
                .param("statusId", view.statusId())
                .param("statusName", view.statusName())
                .param("statusCategory", view.statusCategory())
                .param("priority", view.priority())
                .param("assigneeId", view.assigneeId())
                .param("reporterId", view.reporterId())
                .param("parentId", view.parentId())
                .param("storyPoints", view.storyPoints())
                .param("dueDate", view.dueDate())
                .param("rank", view.rank())
                .param("commentText", view.commentText())
                .param("createdAt", view.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", view.updatedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    /**
     * Removing an already-absent document is success, not an error.
     */
    public void remove(UUID issueId) {
        jdbc.sql("DELETE FROM issue_search_index WHERE issue_id = :issueId")
                .param("issueId", issueId)
                .update();
    }
}
