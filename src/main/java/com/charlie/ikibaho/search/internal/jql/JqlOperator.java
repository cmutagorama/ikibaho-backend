package com.charlie.ikibaho.search.internal.jql;

import java.util.Arrays;
import java.util.Optional;

public enum JqlOperator {
    EQUALS("=", "="),
    NOT_EQUALS("!=", "<>"),
    GREATER(">", ">"),
    GREATER_OR_EQUAL(">=", ">="),
    LESS("<", "<"),
    LESS_OR_EQUAL("<=", "<="),
    /**
     * Text containment; compiles to full-text match or ILIKE depending on field.
     */
    CONTAINS("~", null),
    NOT_CONTAINS("!~", null);

    private final String symbol;
    private final String sql;

    JqlOperator(String symbol, String sql) {
        this.symbol = symbol;
        this.sql = sql;
    }

    public static Optional<JqlOperator> of(String symbol) {
        return Arrays.stream(values()).filter(o -> o.symbol.equals(symbol)).findFirst();
    }

    public String symbol() {
        return symbol;
    }

    public String sql() {
        return sql;
    }

    public boolean isOrdering() {
        return this == GREATER || this == GREATER_OR_EQUAL || this == LESS || this == LESS_OR_EQUAL;
    }

    public boolean isTextMatch() {
        return this == CONTAINS || this == NOT_CONTAINS;
    }
}
