package com.charlie.ikibaho.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

@ConfigurationProperties("ikibaho.security.jwt")
public record JwtProperties(RSAPublicKey publicKey, RSAPrivateKey privateKey, String issuer, Duration accessTokenTtl,
                            Duration refreshTokenTtl) {
}
