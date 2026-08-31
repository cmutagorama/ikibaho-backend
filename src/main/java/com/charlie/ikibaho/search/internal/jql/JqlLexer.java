package com.charlie.ikibaho.search.internal.jql;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns JQL text into tokens.
 * <p>
 * Kept separate from the parser so that quoting, escaping and multi-character
 * operators are solved exactly once. Every error carries the character position,
 * because "invalid query" without a location is unusable in a search box.
 */
final class JqlLexer {
    private final String input;
    private int position;

    JqlLexer(String input) {
        this.input = input;
    }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (position >= input.length()) {
                tokens.add(new Token(TokenType.END, "", position));
                return tokens;
            }
            tokens.add(nextToken());
        }
    }

    private Token nextToken() {
        int start = position;
        char c = input.charAt(position);

        if (c == '(') {
            position++;
            return new Token(TokenType.LEFT_PAREN, "(", start);
        }
        if (c == ')') {
            position++;
            return new Token(TokenType.RIGHT_PAREN, ")", start);
        }
        if (c == ',') {
            position++;
            return new Token(TokenType.COMMA, ",", start);
        }
        if (c == '"' || c == '\'') {
            return quotedString(c);
        }
        if (isOperatorStart(c)) {
            return operator();
        }
        if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == '+') {
            return word();
        }
        throw new JqlSyntaxException("Unexpected character '" + c + "'", start);
    }

    /**
     * Longest match first: without it, ">=" lexes as ">" followed by a stray "=",
     * and "!=" as a negation of nothing.
     */
    private Token operator() {
        int start = position;
        for (String symbol : List.of("!=", ">=", "<=", "!~")) {
            if (input.startsWith(symbol, position)) {
                position += 2;
                return new Token(TokenType.OPERATOR, symbol, start);
            }
        }
        String single = String.valueOf(input.charAt(position));
        position++;
        return new Token(TokenType.OPERATOR, single, start);
    }

    private Token quotedString(char quote) {
        int start = position;
        position++;                        // opening quote
        StringBuilder value = new StringBuilder();
        while (position < input.length()) {
            char c = input.charAt(position);
            if (c == '\\' && position + 1 < input.length()) {
                // Escapes exist so a search for a literal quote is possible at all.
                value.append(input.charAt(position + 1));
                position += 2;
                continue;
            }
            if (c == quote) {
                position++;
                return new Token(TokenType.STRING, value.toString(), start);
            }
            value.append(c);
            position++;
        }
        throw new JqlSyntaxException("Unterminated string", start);
    }

    private Token word() {
        int start = position;
        while (position < input.length()) {
            char c = input.charAt(position);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.' && c != '+' && c != ':') {
                break;
            }
            position++;
        }
        String text = input.substring(start, position);
        TokenType type = switch (text.toUpperCase(java.util.Locale.ROOT)) {
            case "AND" -> TokenType.AND;
            case "OR" -> TokenType.OR;
            case "NOT" -> TokenType.NOT;
            case "IN" -> TokenType.IN;
            case "IS" -> TokenType.IS;
            case "EMPTY", "NULL" -> TokenType.EMPTY;
            case "ORDER" -> TokenType.ORDER;
            case "BY" -> TokenType.BY;
            case "ASC" -> TokenType.ASC;
            case "DESC" -> TokenType.DESC;
            default -> TokenType.WORD;
        };
        return new Token(type, text, start);
    }

    private boolean isOperatorStart(char c) {
        return c == '=' || c == '!' || c == '<' || c == '>' || c == '~';
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    enum TokenType {
        WORD, STRING, OPERATOR,
        AND, OR, NOT, IN, IS, EMPTY,
        ORDER, BY, ASC, DESC,
        LEFT_PAREN, RIGHT_PAREN, COMMA, END
    }

    record Token(TokenType type, String text, int position) {
    }
}
