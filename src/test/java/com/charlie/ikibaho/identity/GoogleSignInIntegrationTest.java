package com.charlie.ikibaho.identity;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.identity.internal.application.AuthService;
import com.charlie.ikibaho.identity.internal.application.FederatedIdentityService;
import com.charlie.ikibaho.identity.internal.application.InvitationService;
import com.charlie.ikibaho.identity.internal.domain.IdentityProvider;
import com.charlie.ikibaho.identity.internal.federation.GoogleIdentityVerifier;
import com.charlie.ikibaho.identity.internal.federation.StubGoogleVerifier;
import com.charlie.ikibaho.identity.internal.persistence.UserIdentityRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account-linking behaviour, with token verification stubbed.
 *
 * GoogleIdentityVerifierTest already proves the token checks against real
 * signatures; repeating that here would be slow and would obscure what these
 * tests are about, which is what happens to accounts once an identity is
 * trusted.
 */
@Import(StubGoogleVerifier.class)
class GoogleSignInIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "test-password-1234";
    private static final String GOOGLE_SUB = "109876543210987654321";

    @Autowired
    FederatedIdentityService federated;
    @Autowired
    AuthService auth;
    @Autowired
    InvitationService invitations;
    @Autowired
    UserService users;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserIdentityRepository identities;
    @Autowired
    StubGoogleVerifier.Stub google;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetStub() {
        google.reset();
    }

    private String tokenFor(String subject, String email) {
        return google.willReturn(new GoogleIdentityVerifier.GoogleIdentity(
                subject, email, "Charlie", null));
    }

    @Test
    void aNewGoogleAccountGetsAnAccountWorkspaceAndFoundingMembership() {
        var result = federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, "Acme");

        assertThat(result.tokens()).isNotNull();
        assertThat(result.organizations()).singleElement()
                .satisfies(m -> {
                    assertThat(m.slug()).isEqualTo("acme");
                    assertThat(m.role()).isEqualTo("ADMIN");
                });
        assertThat(userRepository.findByEmail("charlie@gmail.com")).isPresent();
    }

    @Test
    void aReturningUserIsMatchedOnSubjectNotEmail() {
        federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, "Acme");

        // Same person, address changed at Google. Matching on email would create a
        // second account; matching on sub follows them.
        var again = federated.signIn(tokenFor(GOOGLE_SUB, "charlie.new@gmail.com"), null, null);

        assertThat(again.tokens()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void autoLinksAVerifiedAddressToAnExistingPasswordAccountWithoutBreakingIt() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();

        var viaGoogle = federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, null);

        assertThat(viaGoogle.tokens()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identities.findByUserIdAndProvider(charlie, IdentityProvider.GOOGLE)).isPresent();

        // The password must keep working: linking adds a way in, it does not
        // replace the existing one.
        assertThat(auth.login("charlie@gmail.com", PASSWORD, null).tokens()).isNotNull();
    }

    @Test
    void anInvitationIsAcceptedByProvingControlOfTheInvitedAddress() {
        UUID org = fixtures.organization("Acme");
        UUID admin = fixtures.user(org, "admin@acme.test");
        var invite = invitations.invite(org, admin, "newcomer@gmail.com", "Newcomer");

        var result = federated.acceptInvitation(invite.rawToken(),
                tokenFor(GOOGLE_SUB, "newcomer@gmail.com"));

        assertThat(result.tokens()).isNotNull();
        assertThat(users.userExistsInOrganization(invite.userId(), org)).isTrue();
        // No password was ever set, and that is fine now.
        assertThat(userRepository.findById(invite.userId()).orElseThrow()
                .canAuthenticateWithPassword()).isFalse();
    }

    @Test
    void anInvitationCannotBeAcceptedWithADifferentGoogleAddress() {
        UUID org = fixtures.organization("Acme");
        UUID admin = fixtures.user(org, "admin@acme.test");
        var invite = invitations.invite(org, admin, "newcomer@gmail.com", "Newcomer");

        // This is what makes the Google path stronger than the password one: a
        // forwarded invitation link is useless to whoever received it.
        assertThatThrownBy(() -> federated.acceptInvitation(invite.rawToken(),
                tokenFor(GOOGLE_SUB, "someone.else@gmail.com")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("different address");
    }

    @Test
    void refusesToUnlinkTheOnlyWayIn() {
        federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, "Acme");
        UUID charlie = userRepository.findByEmail("charlie@gmail.com").orElseThrow().getId();

        // No password, so unlinking Google would lock them out of an account that
        // still owns a workspace -- permanently, with no password reset built.
        assertThatThrownBy(() -> federated.unlink(charlie, IdentityProvider.GOOGLE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Set a password");
    }

    @Test
    void unlinkingIsAllowedOnceAPasswordExists() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();
        federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, null);

        federated.unlink(charlie, IdentityProvider.GOOGLE);

        assertThat(identities.findByUserIdAndProvider(charlie, IdentityProvider.GOOGLE)).isEmpty();
        assertThat(auth.login("charlie@gmail.com", PASSWORD, null).tokens()).isNotNull();
    }

    @Test
    void theDenormalisedFlagAlwaysAgreesWithTheIdentityTable() {
        // ck_app_user_authenticatable is only as true as this column. Drift means
        // either a locked-out account or a constraint that stops guarding anything.
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();

        assertFlagMatchesIdentities();
        federated.signIn(tokenFor(GOOGLE_SUB, "charlie@gmail.com"), null, null);
        assertFlagMatchesIdentities();
        federated.unlink(charlie, IdentityProvider.GOOGLE);
        assertFlagMatchesIdentities();
    }

    private void assertFlagMatchesIdentities() {
        Long mismatched = jdbc.sql("""
                        SELECT count(*) FROM app_user u
                        WHERE u.has_federated_identity
                              <> EXISTS (SELECT 1 FROM user_identity i WHERE i.user_id = u.id)
                        """)
                .query(Long.class).single();

        assertThat(mismatched).as("accounts whose flag disagrees with user_identity").isZero();
    }
}
