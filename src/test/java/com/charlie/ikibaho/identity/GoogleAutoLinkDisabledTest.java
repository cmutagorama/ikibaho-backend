package com.charlie.ikibaho.identity;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.identity.internal.application.AuthService;
import com.charlie.ikibaho.identity.internal.application.FederatedIdentityService;
import com.charlie.ikibaho.identity.internal.federation.GoogleIdentityVerifier;
import com.charlie.ikibaho.identity.internal.federation.StubGoogleVerifier;
import com.charlie.ikibaho.platform.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auto-linking off: a verified Google address must not attach itself to an
 * existing password account.
 *
 * A separate context rather than a runtime toggle, because GoogleProperties is a
 * record bound from configuration -- and testing it through the real binding is
 * the point, since this is the switch an operator would actually flip.
 */
@Import(StubGoogleVerifier.class)
@TestPropertySource(properties = "ikibaho.security.google.allow-auto-link=false")
class GoogleAutoLinkDisabledTest extends AbstractIntegrationTest {

    @Autowired
    FederatedIdentityService federated;
    @Autowired
    AuthService auth;
    @Autowired
    StubGoogleVerifier.Stub google;

    @Test
    void refusesToAttachGoogleToAnExistingAccount() {
        auth.register("charlie@gmail.com", "test-password-1234", "Charlie", "Acme");

        String token = google.willReturn(new GoogleIdentityVerifier.GoogleIdentity(
                "109876543210987654321", "charlie@gmail.com", "Charlie", null));

        assertThatThrownBy(() -> federated.signIn(token, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("link Google from settings");
    }
}
