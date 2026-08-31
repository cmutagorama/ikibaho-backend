package com.charlie.ikibaho.platform.error;

public class NotFoundException extends DomainException {
    public NotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id));
    }

    public NotFoundException(String message) {
        super(message);
    }
}
