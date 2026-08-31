package com.charlie.ikibaho.search.internal.query;

import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.search.internal.jql.Jql;
import com.charlie.ikibaho.search.internal.jql.JqlField;
import com.charlie.ikibaho.search.internal.jql.JqlOperator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Turns a parsed query into SQL plus bound parameters.
 * <p>
 * Two invariants hold here, and neither is negotiable:
 *
 * <ol>
 *   <li><b>No user text ever reaches the SQL string.</b> Values become named
 *       parameters; column names come from the {@link JqlField} enum. There is
 *       no path by which typed input becomes SQL syntax.</li>
 *   <li><b>The project scope is ANDed on last</b>, outside whatever the user
 *       wrote. A user cannot widen it with an OR, because their entire condition
 *       is wrapped in parentheses first.</li>
 * </ol>
 */
@Component
public class JqlCompiler {
    /**
     * Newest first: the sane default when the user has not said otherwise.
     */
    private static final String DEFAULT_ORDER = "updated_at DESC, issue_id DESC";

    public CompiledQuery compile(Jql.Query query, SearchContext context) {
        Map<String, Object> parameters = new HashMap<>();
        Counter counter = new Counter();

        String userClause = query.where() == null
                ? null
                : condition(query.where(), context, parameters, counter);

        StringBuilder where = new StringBuilder("organization_id = :orgId");
        parameters.put("orgId", context.organizationId());

        // Scope, applied outside the user's expression. Wrapping theirs in parens
        // is what stops "assignee = me OR project = other" from escaping it.
        if (context.browsableProjectIds().isEmpty()) {
            // No readable projects at all: a query that matches nothing beats one
            // that returns an empty IN () and fails to parse.
            where.append(" AND false");
        } else {
            where.append(" AND project_id IN (:scopeProjectIds)");
            parameters.put("scopeProjectIds", context.browsableProjectIds());
        }

        if (userClause != null) {
            where.append(" AND (").append(userClause).append(')');
        }

        return new CompiledQuery(where.toString(), orderBy(query.orderBy()), parameters);
    }

    private String condition(Jql.Condition node, SearchContext context,
                             Map<String, Object> parameters, Counter counter) {
        return switch (node) {
            case Jql.And and -> join(and.parts(), " AND ", context, parameters, counter);
            case Jql.Or or -> join(or.parts(), " OR ", context, parameters, counter);
            case Jql.Not not -> "NOT (" + condition(not.part(), context, parameters, counter) + ')';
            case Jql.Comparison comparison -> comparison(comparison, context, parameters, counter);
            case Jql.InList inList -> inList(inList, context, parameters, counter);
            case Jql.IsEmpty isEmpty -> isEmpty.field().column()
                    + (isEmpty.negated() ? " IS NOT NULL" : " IS NULL");
            case Jql.FullText fullText -> fullText(fullText, parameters, counter);
        };
    }

    private String join(List<Jql.Condition> parts, String separator, SearchContext context,
                        Map<String, Object> parameters, Counter counter) {
        List<String> compiled = new ArrayList<>(parts.size());
        for (Jql.Condition part : parts) {
            compiled.add('(' + condition(part, context, parameters, counter) + ')');
        }
        return String.join(separator, compiled);
    }

    private String comparison(Jql.Comparison node, SearchContext context,
                              Map<String, Object> parameters, Counter counter) {
        JqlField field = node.field();
        JqlOperator operator = node.operator();

        if (operator.isTextMatch()) {
            // ~ on a named column is substring matching, not full-text: someone
            // writing summary ~ "auth" means the word appears in the summary, and
            // expects "authentication" to match.
            String name = counter.next(parameters, "%" + literal(node.value(), context) + "%");
            String test = field.column() + " ILIKE :" + name;
            return operator == JqlOperator.NOT_CONTAINS
                    ? "(" + test + ") IS NOT TRUE"
                    : test;
        }

        if (operator.isOrdering() && field.valueType() == JqlField.ValueType.TEXT) {
            throw new ValidationException(
                    "Cannot use " + operator.symbol() + " on the text field '" + field.keyword() + "'");
        }

        Object value = typed(field, node.value(), context);
        String name = counter.next(parameters, value);

        // A plain != on a nullable column silently drops the unassigned rows,
        // because NULL <> 'x' is unknown rather than true. Almost nobody means
        // that, so it is spelled out.
        if (operator == JqlOperator.NOT_EQUALS && isNullable(field)) {
            return "(" + field.column() + " IS NULL OR " + field.column() + " <> :" + name + ")";
        }
        return field.column() + ' ' + operator.sql() + " :" + name;
    }

    private String inList(Jql.InList node, SearchContext context,
                          Map<String, Object> parameters, Counter counter) {
        List<Object> values = new ArrayList<>(node.values().size());
        for (Jql.Value value : node.values()) {
            values.add(typed(node.field(), value, context));
        }
        String name = counter.next(parameters, values);
        String test = node.field().column() + " IN (:" + name + ')';

        return node.negated()
                ? "(" + node.field().column() + " IS NULL OR NOT (" + test + "))"
                : test;
    }

    private String fullText(Jql.FullText node, Map<String, Object> parameters, Counter counter) {
        // plainto_tsquery, not to_tsquery: it takes whatever a person typed and
        // never throws on punctuation. to_tsquery would turn a stray "&" into a
        // 500 on the search box.
        String name = counter.next(parameters, node.phrase());
        String test = "search_vector @@ plainto_tsquery('english', :" + name + ')';
        return node.negated() ? "NOT (" + test + ')' : test;
    }

    private Object typed(JqlField field, Jql.Value value, SearchContext context) {
        String raw = literal(value, context);

        return switch (field.valueType()) {
            case TEXT -> raw;
            case NUMBER -> number(field, raw);
            case DATE -> date(field, raw);
            case TIMESTAMP -> timestamp(field, raw);
            case UUID, USER, PROJECT -> uuid(field, value, context, raw);
        };
    }

    private String literal(Jql.Value value, SearchContext context) {
        return switch (value) {
            case Jql.Literal literal -> literal.raw();
            case Jql.CurrentUser ignored -> context.currentUserId().toString();
        };
    }

    private Object uuid(JqlField field, Jql.Value value, SearchContext context, String raw) {
        if (value instanceof Jql.CurrentUser) {
            return context.currentUserId();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    field.keyword() + " expects an id or currentUser(), not '" + raw + "'");
        }
    }

    private Object number(JqlField field, String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new ValidationException(field.keyword() + " expects a number, not '" + raw + "'");
        }
    }

    private Object date(JqlField field, String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                    field.keyword() + " expects a date as YYYY-MM-DD, not '" + raw + "'");
        }
    }

    private Object timestamp(JqlField field, String raw) {
        // A bare date is accepted and read as midnight UTC, because "created >
        // 2026-01-01" is what people write and it should not be a syntax error.
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                throw new ValidationException(
                        field.keyword() + " expects a date or timestamp, not '" + raw + "'");
            }
        }
    }

    private boolean isNullable(JqlField field) {
        return switch (field) {
            case ASSIGNEE, PARENT, STORY_POINTS, DUE, DESCRIPTION -> true;
            default -> false;
        };
    }

    private String orderBy(List<Jql.OrderBy> order) {
        if (order.isEmpty()) {
            return DEFAULT_ORDER;
        }
        List<String> parts = new ArrayList<>(order.size());
        for (Jql.OrderBy clause : order) {
            // NULLS LAST in both directions: an unset field is "no answer", and no
            // answer belongs at the bottom whichever way the column is sorted.
            parts.add(clause.field().column()
                    + (clause.descending() ? " DESC" : " ASC") + " NULLS LAST");
        }
        // Total order, so pagination cannot repeat or skip a row when the sort key ties.
        parts.add("issue_id DESC");
        return String.join(", ", parts);
    }

    /**
     * Generates parameter names, so no user text is ever interpolated.
     */
    private static final class Counter {
        private int next;

        String next(Map<String, Object> parameters, Object value) {
            String name = "p" + next++;
            parameters.put(name, value);
            return name;
        }
    }

    public record CompiledQuery(
            String whereClause,
            String orderByClause,
            Map<String, Object> parameters) {
    }
}
