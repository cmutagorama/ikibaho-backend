package com.charlie.ikibaho.issue.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a comment is added.
 *
 * Carries a short excerpt rather than the whole body: the payload is stored in
 * the publication registry until every listener completes, and a long comment
 * would be duplicated there for no benefit. Anything that needs the full text
 * can read it by {@code commentId}.
 */
public record IssueCommented(
        UUID issueId,
        UUID commentId,
        UUID projectId,
        String issueKey,
        UUID authorId,
        String excerpt,
        Instant occurredAt
) {
    /** Longest excerpt carried on the event; comments run far longer than this. */
    public static final int EXCERPT_LENGTH = 140;

    public static String excerptOf(String body) {
        if (body == null) return "";
        String collapsed = body.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= EXCERPT_LENGTH
                ? collapsed
                : collapsed.substring(0, EXCERPT_LENGTH - 1) + "…";
    }
}
