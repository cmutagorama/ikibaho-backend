package com.charlie.ikibaho.identity;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.identity.internal.application.AuthService;
import com.charlie.ikibaho.identity.internal.application.InvalidCredentialsException;
import com.charlie.ikibaho.identity.internal.application.InvitationService;
import com.charlie.ikibaho.identity.internal.application.LoginResult;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account is global; membership is per organization.
 *
 * These tests exist because the previous model made one address belong to exactly
 * one workspace, which is the assumption a Google identity immediately breaks.
 */
class MembershipIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "test-password-1234";

    @Autowired
    AuthService auth;
    @Autowired
    InvitationService invitations;
    @Autowired
    UserService users;
    @Autowired
    TestFixtures fixtures;

    @Test
    void oneAddressCanBelongToTwoOrganizations() {
        LoginResult registered = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme");
        UUID charlie = registered.userId();
        UUID acme = registered.organizations().getFirst().organizationId();

        UUID northwind = fixtures.organization("Northwind");
        UUID admin = fixtures.user(northwind, "admin@northwind.test");
        var invite = invitations.invite(northwind, admin, "charlie@gmail.com", "Charlie");

        // Already has a password, so the link alone must not be enough to set one.
        assertThat(invite.existingAccount()).isTrue();
        assertThat(invite.userId()).isEqualTo(charlie);

        invitations.acceptAsExistingUser(invite.rawToken(), charlie);

        assertThat(users.membershipsOf(charlie))
                .extracting(UserService.OrganizationMembership::organizationId)
                .containsExactlyInAnyOrder(acme, northwind);
    }

    @Test
    void anEstablishedAccountCannotHaveAPasswordSetByAnInvitationLink() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();

        UUID northwind = fixtures.organization("Northwind");
        UUID admin = fixtures.user(northwind, "admin@northwind.test");
        var invite = invitations.invite(northwind, admin, "charlie@gmail.com", "Charlie");

        // A forwarded invitation must not become an account takeover.
        assertThatThrownBy(() -> invitations.accept(invite.rawToken(), "attacker-password-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sign in and accept");

        // And the real password still works.
        assertThat(auth.login("charlie@gmail.com", PASSWORD, "acme").tokens()).isNotNull();
        assertThat(charlie).isNotNull();
    }

    @Test
    void loginAsksWhichWorkspaceWhenThereAreSeveral() {
        auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme");
        auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Beta");

        LoginResult ambiguous = auth.login("charlie@gmail.com", PASSWORD, null);

        assertThat(ambiguous.needsWorkspaceChoice()).isTrue();
        assertThat(ambiguous.tokens()).isNull();
        assertThat(ambiguous.organizations()).hasSize(2);

        LoginResult chosen = auth.login("charlie@gmail.com", PASSWORD, "beta");
        assertThat(chosen.needsWorkspaceChoice()).isFalse();
        assertThat(chosen.tokens()).isNotNull();
    }

    @Test
    void aSingleWorkspaceStillLogsStraightIn() {
        auth.register("solo@acme.test", PASSWORD, "Solo", "Acme");

        assertThat(auth.login("solo@acme.test", PASSWORD, null).tokens()).isNotNull();
    }

    @Test
    void refusesToCreateAWorkspaceForAnAddressYouDoNotControl() {
        auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme");

        // Otherwise anyone could attach a workspace to somebody else's account.
        assertThatThrownBy(() -> auth.register("charlie@gmail.com", "wrong-password-99",
                "Impostor", "Evil Corp"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void switchingWorkspaceReissuesTheSessionAgainstTheOtherOne() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();
        auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Beta");

        assertThat(auth.switchOrganization(charlie, "beta").tokens()).isNotNull();
    }

    @Test
    void cannotSwitchIntoAWorkspaceYouDoNotBelongTo() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();
        fixtures.organization("Northwind");

        assertThatThrownBy(() -> auth.switchOrganization(charlie, "northwind"))
                .isInstanceOf(com.charlie.ikibaho.platform.error.NotFoundException.class);
    }

    @Test
    void removingSomeoneFromOneWorkspaceLeavesTheOtherIntact() {
        LoginResult registered = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme");
        UUID charlie = registered.userId();

        UUID northwind = fixtures.organization("Northwind");
        UUID admin = fixtures.user(northwind, "admin@northwind.test");
        var invite = invitations.invite(northwind, admin, "charlie@gmail.com", "Charlie");
        invitations.acceptAsExistingUser(invite.rawToken(), charlie);

        invitations.deactivate(northwind, admin, charlie);

        assertThat(users.membershipsOf(charlie)).hasSize(1);
        assertThat(users.userExistsInOrganization(charlie, northwind)).isFalse();
        // Still signed in to the one they were not removed from.
        assertThat(auth.login("charlie@gmail.com", PASSWORD, null).tokens()).isNotNull();
    }

    @Test
    void anInvitedMemberIsNotYetAMember() {
        UUID org = fixtures.organization("Acme");
        UUID admin = fixtures.user(org, "admin@acme.test");
        var invite = invitations.invite(org, admin, "new@acme.test", "New");

        // Invited but unaccepted must not be assignable to issues.
        assertThat(invite.existingAccount()).isFalse();
        assertThat(users.userExistsInOrganization(invite.userId(), org)).isFalse();

        invitations.accept(invite.rawToken(), PASSWORD);
        assertThat(users.userExistsInOrganization(invite.userId(), org)).isTrue();
    }

    @Test
    void anInvitationCannotBeAcceptedByAnotherAccount() {
        UUID org = fixtures.organization("Acme");
        UUID admin = fixtures.user(org, "admin@acme.test");
        UUID interloper = fixtures.account("someone@else.test");
        var invite = invitations.invite(org, admin, "invited@acme.test", "Invited");

        assertThatThrownBy(() -> invitations.acceptAsExistingUser(invite.rawToken(), interloper))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminOfOneWorkspaceIsNotAdminOfAnother() {
        UUID charlie = auth.register("charlie@gmail.com", PASSWORD, "Charlie", "Acme").userId();

        UUID northwind = fixtures.organization("Northwind");
        UUID admin = fixtures.user(northwind, "admin@northwind.test");
        var invite = invitations.invite(northwind, admin, "charlie@gmail.com", "Charlie");
        invitations.acceptAsExistingUser(invite.rawToken(), charlie);

        // Founder of Acme, plain member of Northwind. The old GlobalRole would have
        // made them an admin of both -- a privilege escalation by invitation.
        assertThat(users.membership(charlie, northwind).orElseThrow().role()).isEqualTo("MEMBER");
        assertThat(users.membershipsOf(charlie))
                .filteredOn(m -> m.slug().equals("acme"))
                .singleElement()
                .satisfies(m -> assertThat(m.role()).isEqualTo("ADMIN"));
    }

    @Test
    void inviteRefusesSomeoneAlreadyInTheOrganization() {
        UUID org = fixtures.organization("Acme");
        UUID admin = fixtures.user(org, "admin@acme.test");
        invitations.invite(org, admin, "new@acme.test", "New");

        assertThatThrownBy(() -> invitations.invite(org, admin, "new@acme.test", "New"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in this organization");
    }

    @Test
    void anAccountWithNoWorkspaceCannotSignIn() {
        fixtures.account("nowhere@acme.test");

        assertThatThrownBy(() -> auth.login("nowhere@acme.test", PASSWORD, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no active workspace");
    }
}
