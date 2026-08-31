package com.charlie.ikibaho.search.internal.jql;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The fields JQL may name, and the columns they map to.
 * <p>
 * This enum is the whole defence against SQL injection on the left-hand side of
 * a clause. Column names cannot be bound as parameters, so the only safe way to
 * put one in a query is to have never accepted it as input -- an unrecognised
 * field is rejected by the parser and never reaches the compiler.
 */
public enum JqlField {
    PROJECT("project", "project_id", ValueType.PROJECT),
    KEY("key", "issue_key", ValueType.TEXT),
    SUMMARY("summary", "summary", ValueType.TEXT),
    DESCRIPTION("description", "description", ValueType.TEXT),
    TYPE("type", "type_name", ValueType.TEXT),
    STATUS("status", "status_name", ValueType.TEXT),
    STATUS_CATEGORY("statuscategory", "status_category", ValueType.TEXT),
    PRIORITY("priority", "priority", ValueType.TEXT),
    ASSIGNEE("assignee", "assignee_id", ValueType.USER),
    REPORTER("reporter", "reporter_id", ValueType.USER),
    PARENT("parent", "parent_id", ValueType.UUID),
    STORY_POINTS("storypoints", "story_points", ValueType.NUMBER),
    DUE("due", "due_date", ValueType.DATE),
    CREATED("created", "created_at", ValueType.TIMESTAMP),
    UPDATED("updated", "updated_at", ValueType.TIMESTAMP),
    /**
     * Not a column: free text across the whole document.
     */
    TEXT("text", null, ValueType.TEXT);

    private final String keyword;
    private final String column;
    private final ValueType valueType;

    JqlField(String keyword, String column, ValueType valueType) {
        this.keyword = keyword;
        this.column = column;
        this.valueType = valueType;
    }

    public static Optional<JqlField> of(String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT).replace("_", "");
        return Arrays.stream(values()).filter(f -> f.keyword.equals(normalized)).findFirst();
    }

    public static String knownFields() {
        return Arrays.stream(values()).map(f -> f.keyword).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    public String keyword() {
        return keyword;
    }

    public String column() {
        return column;
    }

    public ValueType valueType() {
        return valueType;
    }

    public boolean isFullText() {
        return this == TEXT;
    }

    public enum ValueType {TEXT, NUMBER, DATE, TIMESTAMP, UUID, USER, PROJECT}
}
