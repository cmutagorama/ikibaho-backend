package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.platform.error.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
