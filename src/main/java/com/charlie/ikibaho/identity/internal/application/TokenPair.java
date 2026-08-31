package com.charlie.ikibaho.identity.internal.application;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
