package com.charlie.ikibaho.search.internal.jql;

import com.charlie.ikibaho.platform.error.ValidationException;

/**
 * A malformed query is the user's typo, not a server fault -- so it extends
 * ValidationException and surfaces as 400 through the existing handler.
 * <p>
 * The position is part of the message because a search box can underline it.
 */
public class JqlSyntaxException extends ValidationException {
    private final int position;

    public JqlSyntaxException(String message, int position) {
        super(message + " (at character " + position + ")");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
