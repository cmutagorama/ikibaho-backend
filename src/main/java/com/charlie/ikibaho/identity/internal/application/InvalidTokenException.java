package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.platform.error.UnauthorizedException;

public class InvalidTokenException extends UnauthorizedException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
