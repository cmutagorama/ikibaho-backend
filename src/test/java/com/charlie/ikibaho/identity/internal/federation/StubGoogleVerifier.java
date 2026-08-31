package com.charlie.ikibaho.identity.internal.federation;

import com.charlie.ikibaho.identity.internal.application.InvalidTokenException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out opaque tokens that map to identities, without any crypto.
 * <p>
 * GoogleIdentityVerifierTest already proves the real verification against real
 * signatures, a real JWKS endpoint and deliberately malformed tokens. Repeating
 * that in every account-linking test would be slow and would bury the thing
 * those tests are actually about.
 * <p>
 * In the federation package because the verifier's constructor is deliberately
 * package-private -- it publishes no JwtDecoder bean, so it cannot be assembled
 * from outside.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubGoogleVerifier {
    @Bean
    @Primary
    Stub stubGoogleVerifier(GoogleProperties properties) {
        return new Stub(properties);
    }

    public static class Stub extends GoogleIdentityVerifier {
        private final Map<String, GoogleIdentity> issued = new ConcurrentHashMap<>();

        Stub(GoogleProperties properties) {
            super(properties);
        }

        /**
         * @return an opaque token that {@link #verify} will resolve to this identity
         */
        public String willReturn(GoogleIdentity identity) {
            String token = UUID.randomUUID().toString();
            issued.put(token, identity);
            return token;
        }

        @Override
        public GoogleIdentity verify(String idToken) {
            GoogleIdentity identity = issued.get(idToken);
            if (identity == null) {
                // Same message the real verifier gives, so a test cannot pass by
                // depending on a distinguishable stub failure.
                throw new InvalidTokenException("Invalid Google credentials");
            }
            return identity;
        }

        public void reset() {
            issued.clear();
        }
    }
}
