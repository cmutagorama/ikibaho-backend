package com.charlie.ikibaho.search;

import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.search.internal.jql.JqlParser;
import com.charlie.ikibaho.search.internal.query.JqlCompiler;
import com.charlie.ikibaho.search.internal.query.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JqlCompilerTest {
    private final JqlParser parser = new JqlParser();
    private final JqlCompiler compiler = new JqlCompiler();

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final SearchContext context = new SearchContext(userId, orgId, Set.of(projectId));

    private JqlCompiler.CompiledQuery compile(String jql) {
        return compiler.compile(parser.parse(jql), context);
    }

    @Test
    void alwaysScopesToTheOrganizationAndBrowsableProjects() {
        JqlCompiler.CompiledQuery compiled = compile("status = Done");

        assertThat(compiled.whereClause()).startsWith("organization_id = :orgId");
        assertThat(compiled.whereClause()).contains("project_id IN (:scopeProjectIds)");
        assertThat(compiled.parameters()).containsEntry("scopeProjectIds", Set.of(projectId));
    }

    @Test
    void wrapsTheUserConditionSoAnOrCannotEscapeTheScope() {
        JqlCompiler.CompiledQuery compiled = compile("status = Done OR status = Open");

        // The user's whole expression must sit inside one set of parentheses,
        // ANDed to the scope. Without this, an OR would match other projects.
        assertThat(compiled.whereClause())
                .contains("AND (")
                .endsWith(")");
    }

    @Test
    void matchesNothingWhenTheUserCanBrowseNoProjects() {
        SearchContext empty = new SearchContext(userId, orgId, Set.of());

        JqlCompiler.CompiledQuery compiled = compiler.compile(parser.parse("status = Done"), empty);

        assertThat(compiled.whereClause()).contains("AND false");
    }

    @Test
    void bindsEveryValueRatherThanInliningIt() {
        JqlCompiler.CompiledQuery compiled = compile("summary ~ \"'; DROP TABLE issue; --\"");

        // The payload must appear only as a bound parameter, never in the SQL.
        assertThat(compiled.whereClause()).doesNotContain("DROP TABLE");
        assertThat(compiled.parameters().values())
                .anySatisfy(v -> assertThat(v.toString()).contains("DROP TABLE"));
    }

    @Test
    void resolvesCurrentUserAtCompileTimeNotParseTime() {
        JqlCompiler.CompiledQuery compiled = compile("assignee = currentUser()");

        assertThat(compiled.parameters()).containsValue(userId);
    }

    @Test
    void notEqualsStillMatchesRowsWhereTheFieldIsNull() {
        JqlCompiler.CompiledQuery compiled = compile("assignee != " + UUID.randomUUID());

        // NULL <> 'x' is unknown, so a bare <> would silently hide unassigned
        // issues from a query that plainly includes them.
        assertThat(compiled.whereClause()).contains("assignee_id IS NULL OR");
    }

    @Test
    void compilesFullTextToATsQuery() {
        JqlCompiler.CompiledQuery compiled = compile("text ~ \"login failure\"");

        assertThat(compiled.whereClause()).contains("search_vector @@ plainto_tsquery('english'");
    }

    @Test
    void appendsATotalOrderSoPagesCannotRepeatARow() {
        assertThat(compile("ORDER BY priority DESC").orderByClause())
                .isEqualTo("priority DESC NULLS LAST, issue_id DESC");
    }

    @Test
    void defaultsToNewestFirst() {
        assertThat(compile("status = Done").orderByClause()).isEqualTo("updated_at DESC, issue_id DESC");
    }

    @Test
    void rejectsANonNumericValueForANumericField() {
        assertThatThrownBy(() -> compile("storyPoints = banana"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("expects a number");
    }

    @Test
    void acceptsABareDateForATimestampField() {
        assertThat(compile("created > 2026-01-01").parameters()).isNotEmpty();
    }

    @Test
    void rejectsOrderingOperatorsOnTextFields() {
        assertThatThrownBy(() -> compile("summary > abc"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot use >");
    }
}
