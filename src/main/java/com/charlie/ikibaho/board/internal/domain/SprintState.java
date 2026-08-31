package com.charlie.ikibaho.board.internal.domain;

/**
 * A sprint moves forward only: FUTURE to ACTIVE to COMPLETED.
 *
 * There is no route back. Reopening a completed sprint would make its velocity
 * -- the whole reason to record one -- a number that can change after the fact.
 */
public enum SprintState {
    FUTURE,
    ACTIVE,
    COMPLETED
}
