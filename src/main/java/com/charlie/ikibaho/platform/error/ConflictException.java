package com.charlie.ikibaho.platform.error;

/**
 * Request conflicts with current state: duplicate key, stale version, illegal transition.
 */
public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
