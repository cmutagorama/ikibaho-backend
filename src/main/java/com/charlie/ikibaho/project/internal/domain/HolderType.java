package com.charlie.ikibaho.project.internal.domain;

public enum HolderType {
    PROJECT_ROLE(true),
    GROUP(true),
    USER(true),
    REPORTER(false),
    ASSIGNEE(false),
    PROJECT_LEAD(false),
    ANY_LOGGED_IN(false);

    private final boolean requiresReference;

    HolderType(boolean requiresReference) {
        this.requiresReference = requiresReference;
    }

    public boolean requiresReference() {
        return requiresReference;
    }

    public boolean isDynamic() {
        return !requiresReference;
    }
}
