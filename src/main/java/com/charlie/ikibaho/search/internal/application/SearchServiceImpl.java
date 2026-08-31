package com.charlie.ikibaho.search.internal.application;

import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.search.SearchHit;
import com.charlie.ikibaho.search.SearchResults;
import com.charlie.ikibaho.search.SearchService;
import com.charlie.ikibaho.search.internal.jql.Jql;
import com.charlie.ikibaho.search.internal.jql.JqlParser;
import com.charlie.ikibaho.search.internal.persistence.SearchQueries;
import com.charlie.ikibaho.search.internal.query.JqlCompiler;
import com.charlie.ikibaho.search.internal.query.SearchContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {
    /**
     * Beyond this, the answer is "refine your query", not a longer page.
     */
    private static final int MAX_LIMIT = 100;

    private final JqlParser parser;
    private final JqlCompiler compiler;
    private final SearchQueries queries;
    private final PermissionService permissions;
    private final CurrentUser currentUser;

    SearchServiceImpl(JqlParser parser, JqlCompiler compiler, SearchQueries queries,
                      PermissionService permissions, CurrentUser currentUser) {
        this.parser = parser;
        this.compiler = compiler;
        this.queries = queries;
        this.permissions = permissions;
        this.currentUser = currentUser;
    }

    @Override
    public SearchResults search(String jql, UUID actorId, int limit, int offset) {
        Jql.Query parsed = parser.parse(jql);
        SearchContext context = contextFor(actorId);
        JqlCompiler.CompiledQuery compiled = compiler.compile(parsed, context);

        int cappedLimit = Math.clamp(limit, 1, MAX_LIMIT);
        int safeOffset = Math.max(offset, 0);

        List<SearchHit> hits = queries.search(compiled, cappedLimit, safeOffset);
        long total = queries.count(compiled);

        return new SearchResults(hits, total, safeOffset, cappedLimit);
    }

    @Override
    public void validate(String jql) {
        parser.parse(jql);   // throws JqlSyntaxException, which is a 400
    }

    /**
     * Resolves the scope once per search.
     * <p>
     * browsableProjectIds is the same set every list endpoint uses, so a project
     * someone loses access to disappears from search at the same moment it
     * disappears everywhere else.
     */
    private SearchContext contextFor(UUID actorId) {
        // The session's workspace, not the account's. A person can now belong to
        // several, and search must be scoped to the one they are looking at --
        // reading it off the user would have searched an arbitrary one of them.
        return new SearchContext(actorId, currentUser.requireOrganizationId(),
                permissions.browsableProjectIds(actorId));
    }
}
