package com.charlie.ikibaho.identity.internal.federation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param clientId      the OAuth client id this application accepts tokens for.
 *                      Blank disables Google sign-in entirely -- there is no safe
 *                      default, because accepting any audience accepts every Google
 *                      app's tokens.
 * @param allowAutoLink whether a verified Google email may attach itself to an
 *                      existing password account
 */
@ConfigurationProperties(prefix = "ikibaho.security.google")
public record GoogleProperties(
        String clientId,
        @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwkSetUri,
        @DefaultValue("true") boolean allowAutoLink,
        @DefaultValue("PT60S") Duration clockSkew
) {
    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }
}
