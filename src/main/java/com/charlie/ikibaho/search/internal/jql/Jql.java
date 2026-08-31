package com.charlie.ikibaho.search.internal.jql;

import java.util.List;

/**
 * The parsed shape of a JQL query.
 * <p>
 * Sealed, so the compiler's switch over node kinds is exhaustive: adding a node
 * type without teaching the compiler to emit SQL for it is a compile error
 * rather than a query that silently drops a condition.
 */
public final class Jql {
    private Jql() {
    }

    public sealed interface Condition permits And, Or, Not, Comparison, InList, IsEmpty, FullText {
    }

    /**
     * A literal, or the {@code currentUser()} function.
     * <p>
     * currentUser() is resolved at compile time, not parse time. That is what
     * lets a saved filter of "assignee = currentUser()" mean something different
     * and correct for each person who runs it.
     */
    public sealed interface Value permits Literal, CurrentUser {
    }

    public record And(List<Condition> parts) implements Condition {
    }

    public record Or(List<Condition> parts) implements Condition {
    }

    public record Not(Condition part) implements Condition {
    }

    public record Comparison(JqlField field, JqlOperator operator, Value value) implements Condition {
    }

    public record InList(JqlField field, List<Value> values, boolean negated) implements Condition {
    }

    public record IsEmpty(JqlField field, boolean negated) implements Condition {
    }

    /**
     * {@code text ~ "something"} -- matched against the weighted tsvector.
     */
    public record FullText(String phrase, boolean negated) implements Condition {
    }

    public record Literal(String raw) implements Value {
    }

    public record CurrentUser() implements Value {
    }

    public record OrderBy(JqlField field, boolean descending) {
    }

    public record Query(Condition where, List<OrderBy> orderBy) {
    }
}
