package com.charlie.ikibaho.identity.internal.federation;

import com.charlie.ikibaho.identity.internal.application.InvalidTokenException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The verifier, tested offline.
 * <p>
 * In the federation package so it can reach the package-private constructor --
 * the verifier deliberately does not publish a JwtDecoder bean.
 * <p>
 * A local JWKS endpoint and a locally generated keypair stand in for Google, so
 * these run with no network and no Google account -- and, more importantly, let
 * the rejection cases be constructed deliberately. A happy-path test proves
 * almost nothing here: the whole value of this class is what it refuses.
 */
class GoogleIdentityVerifierTest {
    private static final String CLIENT_ID = "ikibaho.apps.googleusercontent.com";
    private static final String KEY_ID = "test-key";

    private static HttpServer jwksServer;
    private static RSAPrivateKey privateKey;
    private static String jwkSetUri;

    @BeforeAll
    static void startJwksEndpoint() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        String jwks = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .keyID(KEY_ID)
                .build()
                .toPublicJWK()
                .toJSONString();
        String body = "{\"keys\":[" + jwks + "]}";

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/certs", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        jwksServer.start();
        jwkSetUri = "http://localhost:" + jwksServer.getAddress().getPort() + "/certs";
    }

    @AfterAll
    static void stopJwksEndpoint() {
        jwksServer.stop(0);
    }

    // ------------------------------------------------------------------ setup

    private GoogleIdentityVerifier verifier() {
        return new GoogleIdentityVerifier(new GoogleProperties(
                CLIENT_ID, jwkSetUri, true, Duration.ofSeconds(60)));
    }

    private String token(JWTClaimsSet.Builder claims) {
        return sign(claims, privateKey);
    }

    private String sign(JWTClaimsSet.Builder claims, RSAPrivateKey key) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A token Google would plausibly issue for this application.
     */
    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("109876543210987654321")
                .claim("email", "charlie@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Charlie")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void acceptsAWellFormedTokenForThisApplication() {
        var identity = verifier().verify(token(validClaims()));

        assertThat(identity.subject()).isEqualTo("109876543210987654321");
        assertThat(identity.email()).isEqualTo("charlie@gmail.com");
        assertThat(identity.displayName()).isEqualTo("Charlie");
    }

    @Test
    void rejectsATokenMintedForADifferentGoogleApplication() {
        // THE test in this file. Every Google app's ID tokens are signed by the
        // same keys, so without an audience check a token issued to any other
        // application -- including one an attacker registers -- authenticates
        // here. Signature, issuer and expiry are all perfectly valid below.
        String otherApp = token(validClaims().audience("someone-else.apps.googleusercontent.com"));

        assertThatThrownBy(() -> verifier().verify(otherApp))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid Google credentials");
    }

    @Test
    void acceptsBothIssuerFormsGoogleActuallyUses() {
        // Google emits both, interchangeably. Validating only one produces a
        // failure that looks intermittent and cannot be reproduced on demand.
        assertThat(verifier().verify(token(validClaims().issuer("https://accounts.google.com"))))
                .isNotNull();
        assertThat(verifier().verify(token(validClaims().issuer("accounts.google.com"))))
                .isNotNull();
    }

    @Test
    void rejectsAnUnknownIssuer() {
        String forged = token(validClaims().issuer("https://accounts.evil.example"));

        assertThatThrownBy(() -> verifier().verify(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String expired = token(validClaims()
                .issueTime(Date.from(Instant.now().minusSeconds(7200)))
                .expirationTime(Date.from(Instant.now().minusSeconds(3600))));

        assertThatThrownBy(() -> verifier().verify(expired))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsATokenSignedByAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        RSAPrivateKey attackerKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

        String forged = sign(validClaims(), attackerKey);

        assertThatThrownBy(() -> verifier().verify(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAnUnverifiedEmail() {
        String unverified = token(validClaims().claim("email_verified", false));

        assertThatThrownBy(() -> verifier().verify(unverified))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void rejectsATokenWithNoAudienceClaimRatherThanCrashing() {
        // aud is optional in a JWT. Without a null guard in the validator this
        // throws NPE out of decode(), which escapes the JwtException catch and
        // surfaces as a 500 instead of a 401.
        String noAudience = token(validClaims().audience((List<String>) null));

        assertThatThrownBy(() -> verifier().verify(noAudience))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsATokenMissingAnIdentity() {
        assertThatThrownBy(() -> verifier().verify(token(validClaims().claim("email", null))))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing an identity");
    }

    @Test
    void refusesEverythingWhenNoClientIdIsConfigured() {
        // A blank client id means the audience check cannot be performed, so the
        // feature must be off rather than permissive.
        GoogleIdentityVerifier disabled = new GoogleIdentityVerifier(
                new GoogleProperties("", jwkSetUri, true, Duration.ofSeconds(60)));

        assertThatThrownBy(() -> disabled.verify(token(validClaims())))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not configured");
    }
}
