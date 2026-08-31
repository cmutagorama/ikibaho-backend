package com.charlie.ikibaho.identity.internal.web.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
