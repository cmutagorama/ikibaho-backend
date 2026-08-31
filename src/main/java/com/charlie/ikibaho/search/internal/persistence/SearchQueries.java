package com.charlie.ikibaho.search.internal.persistence;

import com.charlie.ikibaho.search.SearchHit;
import com.charlie.ikibaho.search.internal.query.JqlCompiler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SearchQueries {
    private static final String COLUMNS = """
            issue_id, project_id, project_key, issue_key, summary,
            type_name, status_name, status_category, priority,
            assignee_id, reporter_id, story_points, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    SearchQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SearchHit> search(JqlCompiler.CompiledQuery query, int limit, int offset) {
        var spec = jdbc.sql("SELECT " + COLUMNS + """
                         FROM issue_search_index
                        WHERE
                        """ + query.whereClause()
                + " ORDER BY " + query.orderByClause()
                + " LIMIT :limitValue OFFSET :offsetValue");

        for (var entry : query.parameters().entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.param("limitValue", limit)
                .param("offsetValue", offset)
                .query(SearchHit.class)
                .list();
    }

    /**
     * The total, for a result count.
     *
     * A separate query rather than a window function on the page: COUNT(*) OVER ()
     * makes Postgres materialise every matching row even when only twenty are
     * wanted, which is the opposite of what a LIMIT is for.
     */
    public long count(JqlCompiler.CompiledQuery query) {
        var spec = jdbc.sql("SELECT count(*) FROM issue_search_index WHERE " + query.whereClause());
        for (var entry : query.parameters().entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(Long.class).single();
    }
}
