package com.charlie.ikibaho.identity.internal.federation;

import com.charlie.ikibaho.identity.internal.application.InvalidTokenException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Verifies a Google ID token and extracts the identity it asserts.
 *
 * <p><b>Note it builds its own decoder rather than exposing a JwtDecoder bean.</b>
 * SecurityConfig already publishes one for our own access tokens, and a second
 * bean of that type would make that injection ambiguous -- or worse, resolve and
 * leave the resource server validating our API's tokens against Google's keys.
 * The decoder stays private to this class.
 */
@Component
public class GoogleIdentityVerifier {
    /**
     * Google issues both forms, interchangeably. Validating only one produces a
     * failure that looks intermittent and is impossible to reproduce on demand.
     */
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private final GoogleProperties properties;
    private final JwtDecoder decoder;

    GoogleIdentityVerifier(GoogleProperties properties) {
        this.properties = properties;
        this.decoder = properties.isEnabled() ? buildDecoder(properties) : null;
    }

    private static JwtDecoder buildDecoder(GoogleProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();

        decoder.setJwtValidator((new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),

                // Every Google application's ID tokens are signed by these same
                // keys. Without this check, a token minted for ANY other Google
                // app authenticates here -- a complete authentication bypass, and
                // the usual way this integration is got wrong.
                // The null guard matters: `aud` is optional in a JWT, and without
                // it a token lacking the claim throws NPE from inside the
                // validator -- which escapes decode() as an NPE rather than a
                // JwtException, so it surfaces as a 500 instead of a 401.
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(properties.clientId())),

                new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUERS::contains)
        )));

        return decoder;
    }

    public GoogleIdentity verify(String idToken) {
        if (decoder == null) {
            throw new InvalidTokenException("Google sign-in is not configured");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // Never echo the underlying reason: it tells an attacker which of
            // signature, audience, issuer or expiry they still need to satisfy.
            throw new InvalidTokenException("Invalid Google credentials");
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean verified = jwt.getClaimAsBoolean("email_verified");

        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidTokenException("Google credentials are missing an identity");
        }

        // Google sets this for gmail.com and for verified Workspace domains, but
        // not universally. An unverified address proves nothing about who is
        // holding the token, so it cannot create or link anything.
        if (!Boolean.TRUE.equals(verified)) {
            throw new InvalidTokenException("That Google account's email is not verified");
        }

        return new GoogleIdentity(subject, email.trim().toLowerCase(), jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    public record GoogleIdentity(String subject, String email, String displayName, String avatarUrl) {
    }
}
