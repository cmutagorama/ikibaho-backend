package com.charlie.ikibaho.integration.internal.domain;

import java.util.List;
import java.util.Set;

/**
 * The event names a webhook may subscribe to.
 * <p>
 * These are the API's names, not the Java class names. Renaming an event record
 * must not silently change what subscribers receive, so the mapping is written
 * out once here and nowhere else.
 */
public final class WebhookEvent {
    public static final String ISSUE_CREATED = "issue.created";
    public static final String ISSUE_UPDATED = "issue.updated";
    public static final String ISSUE_DELETED = "issue.deleted";
    public static final String ISSUE_TRANSITIONED = "issue.transitioned";
    public static final String ISSUE_ASSIGNED = "issue.assigned";
    public static final String ISSUE_COMMENTED = "issue.commented";
    public static final String PROJECT_CREATED = "project.created";

    private static final Set<String> ALL = Set.of(
            ISSUE_CREATED, ISSUE_UPDATED, ISSUE_DELETED, ISSUE_TRANSITIONED,
            ISSUE_ASSIGNED, ISSUE_COMMENTED, PROJECT_CREATED);

    private WebhookEvent() {
    }

    public static boolean isKnown(String eventType) {
        return ALL.contains(eventType);
    }

    public static String known() {
        return String.join(", ", ALL.stream().sorted().toList());
    }

    public static List<String> all() {
        return ALL.stream().sorted().toList();
    }
}
