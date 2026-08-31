package com.charlie.ikibaho.platform.error;

/**
 * Anything that should surface as 401.
 *
 * Exists so that the global handler can map authentication failures without
 * naming the modules that raise them. Before this, the handler listed identity's
 * own exception classes by name, which made the shared kernel depend on one of
 * its dependents -- a cycle, and the reason identity could never be extracted.
 *
 * Modules raise their own subclasses; platform only needs to know the category.
 */
public abstract class UnauthorizedException extends DomainException {
    protected UnauthorizedException(String message) {
        super(message);
    }
}
