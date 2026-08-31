package com.charlie.ikibaho.identity.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Transport settings for the refresh-token cookie used by browser clients.
 * <p>
 * The path is deliberately narrow: the browser then only attaches the cookie to
 * the auth endpoints, so it is never sent alongside ordinary API calls.
 */
@ConfigurationProperties("ikibaho.security.refresh-cookie")
public record RefreshCookieProperties(
        @DefaultValue("ikibaho_refresh") String name,
        @DefaultValue(ApiVersion.V1 + "/auth") String path,
        @DefaultValue("true") boolean secure,
        @DefaultValue("Strict") String sameSite) {
}
