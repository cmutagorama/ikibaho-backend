package com.charlie.ikibaho.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Browser origins allowed to call the API.
 * <p>
 * Origins must be listed explicitly -- with allowCredentials the CORS spec forbids
 * the "*" wildcard, and Spring throws at startup if you try. That restriction is the
 * point: any origin allowed here can drive the API with the user's refresh cookie.
 */
@ConfigurationProperties("ikibaho.security.cors")
public record CorsProperties(
        @DefaultValue("") List<String> allowedOrigins,
        @DefaultValue("Location,Link") List<String> exposedHeaders,
        @DefaultValue("1h") Duration maxAge) {

    public boolean isEnabled() {
        return !allowedOrigins.isEmpty();
    }
}
