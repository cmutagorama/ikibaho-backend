package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.internal.domain.Invitation;
import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.persistence.InvitationRepository;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationMemberRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Invites a person into an EXISTING organization.
 *
 * Registration always creates a new org, so without this two people could never
 * share a tenant. Since phase 10 an invitation grants a <em>membership</em>
 * rather than creating a user: inviting somebody who already has an account adds
 * a workspace to it instead of forking them into a second person.
 */
@Service
public class InvitationService {
    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofDays(7);

    private final UserRepository users;
    private final InvitationRepository invitations;
    private final OrganizationMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserService userService;

    InvitationService(UserRepository users, InvitationRepository invitations,
                      OrganizationMemberRepository members, PasswordEncoder passwordEncoder,
                      TokenService tokenService, UserService userService) {
        this.users = users;
        this.invitations = invitations;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public CreatedInvitation invite(UUID organizationId, UUID invitedBy,
                                    String email, String displayName) {
        String normalized = User.normalizeEmail(email);

        User user = users.findByEmail(normalized)
                .orElseGet(() -> users.save(User.invited(normalized, displayName)));

        if (members.existsByUserIdAndOrganizationId(user.getId(), organizationId)) {
            throw new ConflictException("That person is already in this organization");
        }
        members.save(OrganizationMember.invited(organizationId, user.getId()));

        String rawToken = randomToken();
        Instant expiresAt = Instant.now().plus(TTL);
        Invitation invitation = invitations.save(new Invitation(
                organizationId, user.getId(), invitedBy, TokenService.hash(rawToken), expiresAt));

        log.info("Invitation created for {} in org {} (expires {})", normalized, organizationId, expiresAt);

        return new CreatedInvitation(invitation.getId(), user.getId(), normalized, rawToken,
                expiresAt, user.canAuthenticateWithPassword());
    }

    /**
     * Accept for a brand-new account, setting its first password.
     *
     * Only valid when the account has no credential yet. For an address that
     * already has one, see {@link #acceptAsExistingUser} -- letting a link set a
     * password on an established account would turn a forwarded email into an
     * account takeover.
     */
    @Transactional
    public LoginResult accept(String rawToken, String rawPassword) {
        Accepted accepted = consume(rawToken);

        if (accepted.user().canAuthenticateWithPassword()) {
            throw new ConflictException(
                    "That address already has an account; sign in and accept the invitation");
        }
        accepted.user().activateWithPassword(passwordEncoder.encode(rawPassword));
        accepted.membership().activate();

        return LoginResult.signedIn(accepted.user().getId(),
                tokenService.issueForLogin(accepted.user(), accepted.membership()),
                userService.membershipsOf(accepted.user().getId()));
    }

    /**
     * Accept while signed in, for someone who already has an account.
     *
     * Requires a session rather than trusting the link. The link proves only that
     * you received an email; the account it would attach to already exists and
     * has value.
     */
    @Transactional
    public LoginResult acceptAsExistingUser(String rawToken, UUID actorId) {
        Accepted accepted = consume(rawToken);

        if (!accepted.user().getId().equals(actorId)) {
            throw new ForbiddenException("That invitation was sent to a different account");
        }
        accepted.membership().activate();

        return LoginResult.signedIn(actorId,
                tokenService.issueForLogin(accepted.user(), accepted.membership()),
                userService.membershipsOf(actorId));
    }

    @Transactional
    public void revoke(UUID organizationId, UUID userId) {
        Invitation invitation = invitations
                .findFirstByUserIdAndAcceptedAtIsNullAndRevokedAtIsNull(userId)
                .filter(i -> i.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException("Invitation", userId));
        invitation.revoke(Instant.now());

        // The membership was created alongside the invitation, so it goes too --
        // otherwise a revoked invitee lingers in the member list forever.
        members.findByUserIdAndOrganizationId(userId, organizationId)
                .ifPresent(OrganizationMember::deactivate);
    }

    /** Removes somebody from this workspace only. The account is untouched. */
    @Transactional
    public void deactivate(UUID organizationId, UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new ConflictException("You cannot remove yourself from the organization");
        }
        OrganizationMember membership = members.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new NotFoundException("Member", userId));

        membership.deactivate();
        // Access tokens stay valid until they expire (15 min); refresh is cut off
        // now -- and only for this workspace, so their other sessions survive.
        tokenService.revokeForUserInOrganization(userId, organizationId);
    }

    /**
     * The workspace's member list.
     *
     * Status and role come from the membership, not the account: the same person
     * may be an active admin here and a revoked member elsewhere, and this screen
     * is only ever about here.
     */
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID organizationId) {
        Map<UUID, OrganizationMember> byUser = members.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(OrganizationMember::getUserId, Function.identity()));

        return users.findByOrganization(organizationId).stream()
                .map(user -> {
                    OrganizationMember membership = byUser.get(user.getId());
                    return membership == null ? null : new MemberView(
                            user.getId(), user.getEmail(), user.getDisplayName(),
                            user.getAvatarUrl(), membership.getStatus().name(),
                            membership.getRole().name());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Validates and spends an invitation token.
     *
     * Package-private rather than private so federated accept goes through the
     * same expiry, revocation and single-use rules -- a second copy of those is
     * a second place to get them subtly wrong.
     */
    Accepted consume(String rawToken) {
        Instant now = Instant.now();

        Invitation invitation = invitations.findByTokenHash(TokenService.hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired invitation"));

        // One message for spent, revoked and expired alike: distinguishing them
        // tells an attacker which guessed tokens once existed.
        if (!invitation.isPending(now)) {
            throw new InvalidTokenException("Invalid or expired invitation");
        }

        User user = users.findById(invitation.getUserId())
                .orElseThrow(() -> new NotFoundException("User", invitation.getUserId()));
        OrganizationMember membership = members
                .findByUserIdAndOrganizationId(user.getId(), invitation.getOrganizationId())
                .orElseThrow(() -> new NotFoundException("Membership", user.getId()));

        invitation.accept(now);
        return new Accepted(user, membership);
    }

    record Accepted(User user, OrganizationMember membership) {
    }

    public record MemberView(UUID userId, String email, String displayName, String avatarUrl,
                             String status, String role) {
    }

    /**
     * @param existingAccount true when the invitee already has a password, so the
     *                        client shows "sign in to accept" rather than a
     *                        password field
     */
    public record CreatedInvitation(UUID invitationId, UUID userId, String email,
                                    String rawToken, Instant expiresAt,
                                    boolean existingAccount) {
    }
}
