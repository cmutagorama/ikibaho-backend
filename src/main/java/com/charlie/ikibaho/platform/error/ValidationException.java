package com.charlie.ikibaho.platform.error;

/**
 * Request is well-formed but violates a business rule.
 */
public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message);
    }
}
