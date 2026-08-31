package com.charlie.ikibaho.identity.internal.application;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.events.UserRegistered;
import com.charlie.ikibaho.identity.internal.domain.Organization;
import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationMemberRepository;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserService userService;
    private final ApplicationEventPublisher events;

    AuthService(UserRepository users, OrganizationRepository organizations,
                OrganizationMemberRepository members, PasswordEncoder passwordEncoder,
                TokenService tokenService, UserService userService,
                ApplicationEventPublisher events) {
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userService = userService;
        this.events = events;
    }

    /**
     * Self-serve signup: creates an organization and makes the caller its admin.
     *
     * An existing account creating a second workspace is legitimate now, so a
     * known address is no longer a conflict -- it reuses the account and adds a
     * membership. Only a new address creates a new account.
     */
    @Transactional
    public LoginResult register(String email, String rawPassword,
                                String displayName, String organizationName) {
        String normalized = User.normalizeEmail(email);

        String slug = slugify(organizationName);
        if (organizations.existsBySlug(slug)) {
            throw new ConflictException("Organization name is already taken");
        }

        User user = users.findByEmail(normalized).orElse(null);
        if (user == null) {
            user = users.save(new User(normalized,
                    passwordEncoder.encode(rawPassword), displayName));
        } else if (!user.canAuthenticateWithPassword()
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Creating a workspace for an address you do not control would let
            // anyone attach themselves to somebody else's account.
            throw new InvalidCredentialsException("Invalid credentials");
        }

        Organization org = organizations.save(new Organization(organizationName, slug));
        OrganizationMember membership = members.save(
                OrganizationMember.founder(org.getId(), user.getId()));

        events.publishEvent(new UserRegistered(user.getId(), org.getId(), normalized,
                displayName, Instant.now()));

        return LoginResult.signedIn(user.getId(),
                tokenService.issueForLogin(user, membership),
                userService.membershipsOf(user.getId()));
    }

    /**
     * Authenticate, then resolve the workspace.
     *
     * The order matters. The account is global, so the password is checked once
     * against one row; which workspace to enter is a separate question answered
     * afterwards. Answering it first would let anyone enumerate the workspaces an
     * address belongs to without proving they own it.
     */
    @Transactional
    public LoginResult login(String email, String rawPassword, String organizationSlug) {
        User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);

        // Same generic message and comparable work for every branch: never reveal
        // whether an email exists, and don't leak it through response timing
        // either. A federated account is treated exactly like a missing one.
        if (user == null || !user.canAuthenticateWithPassword()) {
            passwordEncoder.encode(rawPassword);      // burn equivalent CPU
            throw new InvalidCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()) || !user.isActive()) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        List<OrganizationMember> active = members.activeFor(user.getId());
        if (active.isEmpty()) {
            throw new ForbiddenException("This account has no active workspace");
        }

        OrganizationMember chosen = resolve(active, organizationSlug);
        if (chosen == null) {
            // Authenticated but ambiguous. Returning the list here leaks nothing:
            // they have already proved they own the account.
            return LoginResult.chooseWorkspace(user.getId(),
                    userService.membershipsOf(user.getId()));
        }
        return LoginResult.signedIn(user.getId(),
                tokenService.issueForLogin(user, chosen),
                userService.membershipsOf(user.getId()));
    }

    /** Reissues the token against a different workspace, for an already-signed-in user. */
    @Transactional
    public LoginResult switchOrganization(UUID userId, String organizationSlug) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        OrganizationMember chosen = resolve(members.activeFor(userId), organizationSlug);
        if (chosen == null) {
            // 404 rather than 403: whether a workspace you cannot enter exists is
            // not information to hand out.
            throw new NotFoundException("Organization", userId);
        }
        return LoginResult.signedIn(userId,
                tokenService.issueForLogin(user, chosen),
                userService.membershipsOf(userId));
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        return tokenService.rotate(refreshToken);
    }

    @Transactional
    public void logout(UUID userId) {
        tokenService.revokeAllForUser(userId);
    }

    /**
     * A single membership is chosen implicitly; several require the caller to say
     * which. Auto-picking one of many would silently drop someone into the wrong
     * workspace, and they would not necessarily notice.
     */
    private OrganizationMember resolve(List<OrganizationMember> active, String slug) {
        if (slug == null || slug.isBlank()) {
            return active.size() == 1 ? active.getFirst() : null;
        }
        UUID organizationId = organizations.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .map(Organization::getId)
                .orElse(null);

        return organizationId == null ? null : active.stream()
                .filter(m -> m.getOrganizationId().equals(organizationId))
                .findFirst()
                .orElse(null);
    }

    private static String slugify(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
