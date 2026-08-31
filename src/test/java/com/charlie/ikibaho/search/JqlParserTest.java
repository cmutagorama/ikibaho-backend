package com.charlie.ikibaho.search;

import com.charlie.ikibaho.search.internal.jql.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JqlParserTest {
    private final JqlParser parser = new JqlParser();

    @Test
    void parsesASingleComparison() {
        Jql.Query query = parser.parse("status = Done");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.Comparison.class, c -> {
            assertThat(c.field()).isEqualTo(JqlField.STATUS);
            assertThat(c.operator()).isEqualTo(JqlOperator.EQUALS);
            assertThat(c.value()).isEqualTo(new Jql.Literal("Done"));
        });
    }

    @Test
    void keepsQuotedValuesWhole() {
        Jql.Query query = parser.parse("status = \"In Progress\"");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.Comparison.class,
                c -> assertThat(c.value()).isEqualTo(new Jql.Literal("In Progress")));
    }

    @Test
    void bindsAndTighterThanOr() {
        // a OR b AND c must group as a OR (b AND c), or every mixed query is wrong.
        Jql.Query query = parser.parse("priority = High OR priority = Low AND status = Done");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.Or.class, or -> {
            assertThat(or.parts()).hasSize(2);
            assertThat(or.parts().get(1)).isInstanceOf(Jql.And.class);
        });
    }

    @Test
    void parenthesesOverridePrecedence() {
        Jql.Query query = parser.parse("(priority = High OR priority = Low) AND status = Done");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.And.class, and -> {
            assertThat(and.parts()).hasSize(2);
            assertThat(and.parts().getFirst()).isInstanceOf(Jql.Or.class);
        });
    }

    @Test
    void parsesInLists() {
        Jql.Query query = parser.parse("status IN (Done, \"In Progress\")");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.InList.class, in -> {
            assertThat(in.negated()).isFalse();
            assertThat(in.values()).containsExactly(
                    new Jql.Literal("Done"), new Jql.Literal("In Progress"));
        });
    }

    @Test
    void parsesNotIn() {
        Jql.Query query = parser.parse("status NOT IN (Done)");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.InList.class,
                in -> assertThat(in.negated()).isTrue());
    }

    @Test
    void parsesIsEmptyAndIsNotEmpty() {
        assertThat(parser.parse("assignee IS EMPTY").where())
                .isInstanceOfSatisfying(Jql.IsEmpty.class, e -> assertThat(e.negated()).isFalse());

        assertThat(parser.parse("assignee IS NOT EMPTY").where())
                .isInstanceOfSatisfying(Jql.IsEmpty.class, e -> assertThat(e.negated()).isTrue());
    }

    @Test
    void treatsTextAsFullTextRatherThanAColumn() {
        Jql.Query query = parser.parse("text ~ \"login bug\"");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.FullText.class,
                t -> assertThat(t.phrase()).isEqualTo("login bug"));
    }

    @Test
    void recognisesCurrentUserAsAFunctionNotAName() {
        Jql.Query query = parser.parse("assignee = currentUser()");

        assertThat(query.where()).isInstanceOfSatisfying(Jql.Comparison.class,
                c -> assertThat(c.value()).isInstanceOf(Jql.CurrentUser.class));
    }

    @Test
    void parsesOrderBy() {
        Jql.Query query = parser.parse("status = Done ORDER BY priority DESC, created ASC");

        assertThat(query.orderBy()).containsExactly(
                new Jql.OrderBy(JqlField.PRIORITY, true),
                new Jql.OrderBy(JqlField.CREATED, false));
    }

    @Test
    void allowsOrderByWithNoConditions() {
        Jql.Query query = parser.parse("ORDER BY created DESC");

        assertThat(query.where()).isNull();
        assertThat(query.orderBy()).hasSize(1);
    }

    @Test
    void anEmptyQueryMeansEverything() {
        assertThat(parser.parse("").where()).isNull();
        assertThat(parser.parse("   ").where()).isNull();
        assertThat(parser.parse(null).where()).isNull();
    }

    @Test
    void namesTheUnknownFieldAndListsTheRealOnes() {
        assertThatThrownBy(() -> parser.parse("banana = 3"))
                .isInstanceOf(JqlSyntaxException.class)
                .hasMessageContaining("Unknown field 'banana'")
                .hasMessageContaining("assignee");
    }

    @Test
    void reportsWhereTheSyntaxErrorIs() {
        assertThatThrownBy(() -> parser.parse("status = Done AND"))
                .isInstanceOf(JqlSyntaxException.class)
                .hasMessageContaining("at character");
    }

    @Test
    void rejectsAnUnterminatedString() {
        assertThatThrownBy(() -> parser.parse("summary ~ \"never closed"))
                .isInstanceOf(JqlSyntaxException.class)
                .hasMessageContaining("Unterminated string");
    }

    @Test
    void lexesMultiCharacterOperatorsAsOneToken() {
        // ">=" must not lex as ">" followed by a stray "=".
        assertThat(parser.parse("storyPoints >= 3").where())
                .isInstanceOfSatisfying(Jql.Comparison.class,
                        c -> assertThat(c.operator()).isEqualTo(JqlOperator.GREATER_OR_EQUAL));

        assertThat(parser.parse("status != Done").where())
                .isInstanceOfSatisfying(Jql.Comparison.class,
                        c -> assertThat(c.operator()).isEqualTo(JqlOperator.NOT_EQUALS));
    }

    @Test
    void keywordsAreCaseInsensitive() {
        assertThat(parser.parse("status = Done and priority = High").where())
                .isInstanceOf(Jql.And.class);
        assertThat(parser.parse("status = Done AND priority = High").where())
                .isInstanceOf(Jql.And.class);
    }
}
