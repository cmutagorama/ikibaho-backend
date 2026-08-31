package com.charlie.ikibaho.search.internal.jql;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive descent over the token stream.
 * <p>
 * The grammar, lowest precedence first:
 * <pre>
 *   query      = [ or ] [ "ORDER" "BY" orderList ]
 *   or         = and { "OR" and }
 *   and        = unary { "AND" unary }
 *   unary      = [ "NOT" ] primary
 *   primary    = "(" or ")" | clause
 *   clause     = field operator value
 *              | field [ "NOT" ] "IN" "(" value { "," value } ")"
 *              | field "IS" [ "NOT" ] "EMPTY"
 *   value      = string | word | "currentUser" "(" ")"
 * </pre>
 * <p>
 * An empty query parses to a null condition, meaning "everything the caller may
 * see" -- which is what an empty search box should return.
 */
@Component
public class JqlParser {
    private List<JqlLexer.Token> tokens;
    private int index;

    public Jql.Query parse(String jql) {
        if (jql == null || jql.isBlank()) {
            return new Jql.Query(null, List.of());
        }
        this.tokens = new JqlLexer(jql).tokenize();
        this.index = 0;

        Jql.Condition where = peek().type() == JqlLexer.TokenType.ORDER ? null : parseOr();
        List<Jql.OrderBy> orderBy = parseOrderBy();

        expect(JqlLexer.TokenType.END, "end of query");
        return new Jql.Query(where, orderBy);
    }

    private Jql.Condition parseOr() {
        List<Jql.Condition> parts = new ArrayList<>();
        parts.add(parseAnd());
        while (match(JqlLexer.TokenType.OR)) {
            parts.add(parseAnd());
        }
        return parts.size() == 1 ? parts.getFirst() : new Jql.Or(parts);
    }

    private Jql.Condition parseAnd() {
        List<Jql.Condition> parts = new ArrayList<>();
        parts.add(parseUnary());
        while (match(JqlLexer.TokenType.AND)) {
            parts.add(parseUnary());
        }
        return parts.size() == 1 ? parts.getFirst() : new Jql.And(parts);
    }

    private Jql.Condition parseUnary() {
        if (match(JqlLexer.TokenType.NOT)) {
            return new Jql.Not(parseUnary());
        }
        return parsePrimary();
    }

    private Jql.Condition parsePrimary() {
        if (match(JqlLexer.TokenType.LEFT_PAREN)) {
            Jql.Condition inner = parseOr();
            expect(JqlLexer.TokenType.RIGHT_PAREN, "')'");
            return inner;
        }
        return parseClause();
    }

    private Jql.Condition parseClause() {
        JqlLexer.Token fieldToken = expect(JqlLexer.TokenType.WORD, "a field name");
        JqlField field = JqlField.of(fieldToken.text())
                .orElseThrow(() -> new JqlSyntaxException(
                        "Unknown field '" + fieldToken.text() + "'. Known fields: "
                                + JqlField.knownFields(), fieldToken.position()));

        // field IS [NOT] EMPTY
        if (match(JqlLexer.TokenType.IS)) {
            boolean negated = match(JqlLexer.TokenType.NOT);
            expect(JqlLexer.TokenType.EMPTY, "EMPTY");
            return new Jql.IsEmpty(field, negated);
        }

        // field [NOT] IN (...)
        boolean negatedIn = false;
        if (peek().type() == JqlLexer.TokenType.NOT
                && peekAt(1).type() == JqlLexer.TokenType.IN) {
            advance();
            negatedIn = true;
        }
        if (match(JqlLexer.TokenType.IN)) {
            expect(JqlLexer.TokenType.LEFT_PAREN, "'(' after IN");
            List<Jql.Value> values = new ArrayList<>();
            do {
                values.add(parseValue());
            } while (match(JqlLexer.TokenType.COMMA));
            expect(JqlLexer.TokenType.RIGHT_PAREN, "')'");
            if (values.isEmpty()) {
                throw new JqlSyntaxException("IN needs at least one value", peek().position());
            }
            return new Jql.InList(field, values, negatedIn);
        }

        // field <op> value
        JqlLexer.Token operatorToken = expect(JqlLexer.TokenType.OPERATOR, "an operator");
        JqlOperator operator = JqlOperator.of(operatorToken.text())
                .orElseThrow(() -> new JqlSyntaxException(
                        "Unknown operator '" + operatorToken.text() + "'", operatorToken.position()));

        Jql.Value value = parseValue();

        if (field.isFullText()) {
            if (!operator.isTextMatch()) {
                throw new JqlSyntaxException(
                        "The text field only supports ~ and !~", operatorToken.position());
            }
            String phrase = value instanceof Jql.Literal literal ? literal.raw() : "";
            return new Jql.FullText(phrase, operator == JqlOperator.NOT_CONTAINS);
        }
        return new Jql.Comparison(field, operator, value);
    }

    private Jql.Value parseValue() {
        JqlLexer.Token token = advance();

        if (token.type() == JqlLexer.TokenType.STRING) {
            return new Jql.Literal(token.text());
        }
        if (token.type() != JqlLexer.TokenType.WORD) {
            throw new JqlSyntaxException("Expected a value but found '" + token.text() + "'",
                    token.position());
        }
        // currentUser() -- the one function, and the reason saved filters are worth
        // sharing at all.
        if (token.text().equalsIgnoreCase("currentUser")
                && peek().type() == JqlLexer.TokenType.LEFT_PAREN) {
            advance();
            expect(JqlLexer.TokenType.RIGHT_PAREN, "')' after currentUser(");
            return new Jql.CurrentUser();
        }
        return new Jql.Literal(token.text());
    }

    private List<Jql.OrderBy> parseOrderBy() {
        if (!match(JqlLexer.TokenType.ORDER)) {
            return List.of();
        }
        expect(JqlLexer.TokenType.BY, "BY after ORDER");

        List<Jql.OrderBy> order = new ArrayList<>();
        do {
            JqlLexer.Token token = expect(JqlLexer.TokenType.WORD, "a field name");
            JqlField field = JqlField.of(token.text())
                    .orElseThrow(() -> new JqlSyntaxException(
                            "Cannot order by '" + token.text() + "'", token.position()));
            if (field.isFullText()) {
                throw new JqlSyntaxException("Cannot order by text", token.position());
            }
            boolean descending = false;
            if (match(JqlLexer.TokenType.DESC)) {
                descending = true;
            } else {
                match(JqlLexer.TokenType.ASC);
            }
            order.add(new Jql.OrderBy(field, descending));
        } while (match(JqlLexer.TokenType.COMMA));

        return order;
    }

    private JqlLexer.Token peek() {
        return tokens.get(index);
    }

    private JqlLexer.Token peekAt(int offset) {
        int target = Math.min(index + offset, tokens.size() - 1);
        return tokens.get(target);
    }

    private JqlLexer.Token advance() {
        JqlLexer.Token token = tokens.get(index);
        if (token.type() != JqlLexer.TokenType.END) {
            index++;
        }
        return token;
    }

    private boolean match(JqlLexer.TokenType type) {
        if (peek().type() == type) {
            advance();
            return true;
        }
        return false;
    }

    private JqlLexer.Token expect(JqlLexer.TokenType type, String description) {
        if (peek().type() != type) {
            String found = peek().type() == JqlLexer.TokenType.END
                    ? "the end of the query"
                    : "'" + peek().text() + "'";
            throw new JqlSyntaxException("Expected " + description + " but found " + found,
                    peek().position());
        }
        return advance();
    }

    /**
     * Normalizes a keyword for comparison; kept here so the rules live together.
     */
    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
