package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.internal.domain.IdentityProvider;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.domain.UserIdentity;
import com.charlie.ikibaho.identity.internal.federation.GoogleIdentityVerifier;
import com.charlie.ikibaho.identity.internal.federation.GoogleIdentityVerifier.GoogleIdentity;
import com.charlie.ikibaho.identity.internal.federation.GoogleProperties;
import com.charlie.ikibaho.identity.internal.persistence.UserIdentityRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sign-in and account linking through an external identity provider.
 * <p>
 * Lives beside AuthService rather than in the federation package so it can reuse
 * that class's workspace resolution and InvitationService's token rules. Google
 * changes how somebody proves who they are; it must not change what happens
 * afterwards, and sharing the code is the only way to guarantee that.
 * <p>
 * The federation package keeps what is genuinely Google-specific: verifying an
 * ID token and reading the claims out of it.
 */
@Service
public class FederatedIdentityService {
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final GoogleIdentityVerifier verifier;
    private final GoogleProperties properties;
    private final AuthService authService;
    private final InvitationService invitations;

    FederatedIdentityService(UserRepository users, UserIdentityRepository identities,
                             GoogleIdentityVerifier verifier, GoogleProperties properties,
                             AuthService authService, InvitationService invitations) {
        this.users = users;
        this.identities = identities;
        this.verifier = verifier;
        this.properties = properties;
        this.authService = authService;
        this.invitations = invitations;
    }

    /**
     * Google does not always send a name; the address is a usable fallback.
     */
    private static String displayName(GoogleIdentity identity) {
        return identity.displayName() == null || identity.displayName().isBlank()
                ? identity.email().split("@")[0]
                : identity.displayName();
    }

    /**
     * Sign in with Google, optionally creating a workspace at the same time.
     *
     * @param organizationSlug which workspace to enter, when the account has more
     *                         than one
     * @param organizationName present to found a new workspace instead
     */
    @Transactional
    public LoginResult signIn(String idToken, String organizationSlug, String organizationName) {
        GoogleIdentity identity = verifier.verify(idToken);

        User user = identities
                .findByProviderAndSubject(IdentityProvider.GOOGLE, identity.subject())
                .map(link -> {
                    // A Google account's address can change. Follow it: the subject
                    // is what identifies the person, the email is only a label.
                    link.refreshEmail(identity.email());
                    return users.findById(link.getUserId())
                            .orElseThrow(() -> new NotFoundException("User", link.getUserId()));
                })
                .orElseGet(() -> attach(identity));

        if (!user.isActive()) {
            throw new ForbiddenException("This account is not active");
        }

        if (organizationName != null && !organizationName.isBlank()) {
            String slug = authService.requireAvailableSlug(organizationName);
            return authService.createWorkspaceFor(user, organizationName, slug);
        }
        return authService.completeLogin(user, organizationSlug);
    }

    /**
     * Accept an invitation with Google instead of setting a password.
     * <p>
     * Stronger than the password path rather than weaker. That one trusts whoever
     * holds the link; this additionally requires proving control of the invited
     * address, so a forwarded invitation is useless to the wrong person.
     */
    @Transactional
    public LoginResult acceptInvitation(String invitationToken, String idToken) {
        GoogleIdentity identity = verifier.verify(idToken);
        InvitationService.Accepted accepted = invitations.consume(invitationToken);

        if (!accepted.user().getEmail().equals(identity.email())) {
            throw new ForbiddenException("That invitation was sent to a different address");
        }

        if (identities.findByUserIdAndProvider(accepted.user().getId(), IdentityProvider.GOOGLE)
                .isEmpty()) {
            link(accepted.user(), identity);
        }
        accepted.user().activateWithFederatedIdentity();
        accepted.membership().activate();

        return authService.completeLogin(accepted.user(), null);
    }

    /**
     * Links Google to the signed-in account, from settings.
     */
    @Transactional
    public void linkToCurrentUser(UUID actorId, String idToken) {
        GoogleIdentity identity = verifier.verify(idToken);
        User user = users.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User", actorId));

        identities.findByProviderAndSubject(IdentityProvider.GOOGLE, identity.subject())
                .ifPresent(existing -> {
                    // Linking to a second account would make "sign in with Google"
                    // ambiguous, and silently move the identity if we allowed it.
                    if (!existing.getUserId().equals(actorId)) {
                        throw new ConflictException(
                                "That Google account is already linked to another account");
                    }
                });

        if (identities.findByUserIdAndProvider(actorId, IdentityProvider.GOOGLE).isPresent()) {
            throw new ConflictException("Google is already linked to this account");
        }
        link(user, identity);
    }

    /**
     * Unlink, refusing to remove the last way in.
     * <p>
     * Without this check somebody with no password unlinks Google and is locked
     * out of an account that still exists and still owns workspaces -- and with
     * no password reset built, permanently.
     */
    @Transactional
    public void unlink(UUID actorId, IdentityProvider provider) {
        User user = users.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User", actorId));

        if (!user.canAuthenticateWithPassword()) {
            throw new ConflictException("Set a password before unlinking your last sign-in method");
        }
        identities.findByUserIdAndProvider(actorId, provider).ifPresent(identities::delete);

        // Recomputed rather than assumed false: a second provider may remain, and
        // ck_app_user_authenticatable is only as true as this flag.
        identities.flush();
        user.setHasFederatedIdentity(identities.existsByUserId(actorId));
    }

    /**
     * First sight of this Google account: either it belongs to an existing
     * Ikibaho account, or it is somebody new.
     */
    private User attach(GoogleIdentity identity) {
        User existing = users.findByEmail(identity.email()).orElse(null);

        if (existing == null) {
            // Activated BEFORE the insert, not after. ck_app_user_authenticatable
            // is evaluated per row as it is written, and a passwordless account
            // with has_federated_identity still false violates it -- so saving
            // first and activating second fails at the INSERT, every time.
            User created = new User(identity.email(), null, displayName(identity));
            created.activateWithFederatedIdentity();

            User saved = users.save(created);
            link(saved, identity);
            return saved;
        }

        // Auto-linking a verified address to an existing password account is
        // exactly as strong as a password reset: both trust control of the
        // mailbox. That holds for Google, and would need re-examining for a
        // provider whose email_verified means less -- which is why the rule is
        // here in code rather than in somebody's memory.
        if (!properties.allowAutoLink()) {
            throw new ConflictException(
                    "An account already exists for that address; sign in and link Google from settings");
        }
        link(existing, identity);
        return existing;
    }

    private void link(User user, GoogleIdentity identity) {
        identities.save(new UserIdentity(user.getId(), IdentityProvider.GOOGLE,
                identity.subject(), identity.email()));
        // Keeps ck_app_user_authenticatable satisfiable for a passwordless account.
        user.setHasFederatedIdentity(true);
    }
}
