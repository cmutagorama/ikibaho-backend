package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements the SPI declared in the project module. Its presence makes the
 *
 * @ConditionalOnMissingBean no-op from Phase 3 back off, which is what turns
 * REPORTER and ASSIGNEE grants from unsatisfiable into live.
 */
@Component
public class JdbcIssueContextResolver implements IssueContextResolver {
    private final JdbcClient jdbc;

    JdbcIssueContextResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IssueContext> resolve(UUID issueId) {
        return jdbc.sql("""
                        SELECT id AS issue_id, project_id, reporter_id, assignee_id
                        FROM issue
                        WHERE id = :issueId AND deleted = false
                        """)
                .param("issueId", issueId)
                .query(IssueContext.class)
                .optional();
    }
}
