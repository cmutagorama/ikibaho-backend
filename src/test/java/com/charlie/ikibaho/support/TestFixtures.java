package com.charlie.ikibaho.support;

import com.charlie.ikibaho.identity.internal.domain.MemberRole;
import com.charlie.ikibaho.identity.internal.domain.Organization;
import com.charlie.ikibaho.identity.internal.domain.OrganizationMember;
import com.charlie.ikibaho.identity.internal.domain.User;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationMemberRepository;
import com.charlie.ikibaho.identity.internal.persistence.OrganizationRepository;
import com.charlie.ikibaho.identity.internal.persistence.UserRepository;
import com.charlie.ikibaho.issue.internal.application.IssueMetadataProvisioning;
import com.charlie.ikibaho.issue.internal.domain.IssueType;
import com.charlie.ikibaho.issue.internal.persistence.IssueTypeRepository;
import com.charlie.ikibaho.project.internal.domain.HolderType;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.project.ProjectSummary;
import com.charlie.ikibaho.project.internal.domain.PermissionGrant;
import com.charlie.ikibaho.project.internal.domain.ProjectRoleActor;
import com.charlie.ikibaho.project.internal.persistence.PermissionGrantRepository;
import com.charlie.ikibaho.project.internal.persistence.ProjectRepository;
import com.charlie.ikibaho.project.internal.persistence.ProjectRoleActorRepository;
import com.charlie.ikibaho.project.internal.persistence.ProjectRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test-only builder for a populated tenant. Registered via @Import on
 * AbstractIntegrationTest -- it is not component-scanned.
 */
public class TestFixtures {
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final ProjectService projects;
    private final ProjectRoleRepository roles;
    private final ProjectRoleActorRepository actors;
    private final IssueTypeRepository issueTypes;
    private final PasswordEncoder passwordEncoder;
    private final PermissionGrantRepository grants;
    private final ProjectRepository projectRepository;
    private final IssueMetadataProvisioning metadataProvisioning;
    private final OrganizationMemberRepository members;

    public TestFixtures(OrganizationRepository organizations, UserRepository users,
                        ProjectService projects, ProjectRoleRepository roles,
                        ProjectRoleActorRepository actors, IssueTypeRepository issueTypes,
                        PasswordEncoder passwordEncoder, PermissionGrantRepository grants, ProjectRepository projectRepository,
                        IssueMetadataProvisioning metadataProvisioning,
                        OrganizationMemberRepository members) {
        this.organizations = organizations;
        this.users = users;
        this.projects = projects;
        this.roles = roles;
        this.actors = actors;
        this.issueTypes = issueTypes;
        this.passwordEncoder = passwordEncoder;
        this.grants = grants;
        this.projectRepository = projectRepository;
        this.metadataProvisioning = metadataProvisioning;
        this.members = members;
    }

    @Transactional
    public UUID organization(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return organizations.save(new Organization(name, slug)).getId();
    }

    /** An account plus an active admin membership of the given workspace. */
    @Transactional
    public UUID user(UUID organizationId, String email) {
        UUID userId = account(email);
        members.save(OrganizationMember.founder(organizationId, userId));
        return userId;
    }

    /**
     * An account with no workspace at all.
     *
     * Needed now that the two are separable -- the login and multi-org tests want
     * a person who exists but has not been admitted anywhere.
     */
    @Transactional
    public UUID account(String email) {
        return users.save(new User(User.normalizeEmail(email),
                passwordEncoder.encode("test-password-1234"), email.split("@")[0])).getId();
    }

    /** Adds an existing account to another workspace. */
    @Transactional
    public void joinOrganization(UUID organizationId, UUID userId, MemberRole role) {
        OrganizationMember member = OrganizationMember.founder(organizationId, userId);
        member.changeRole(role);
        members.save(member);
    }

    @Transactional
    public UUID project(UUID organizationId, String key, UUID leadId) {
        ProjectSummary summary = projects.create(organizationId, key, key + " Project", leadId);
        return summary.id();
    }

    @Transactional
    public void addToRole(UUID projectId, UUID organizationId, String roleName, UUID userId) {
        UUID roleId = roles.findByOrganizationIdAndName(organizationId, roleName)
                .orElseThrow(() -> new IllegalStateException("No such role: " + roleName))
                .getId();
        actors.save(ProjectRoleActor.forUser(projectId, roleId, userId));
    }

    @Transactional
    public void grantToRole(UUID projectId, UUID organizationId, String roleName, Permission permission) {
        UUID schemeId = projectRepository.findById(projectId).orElseThrow().getPermissionSchemeId();
        UUID roleId = roles.findByOrganizationIdAndName(organizationId, roleName).orElseThrow().getId();
        grants.save(new PermissionGrant(schemeId, permission, HolderType.PROJECT_ROLE, roleId));
    }

    public void addViewer(UUID projectId, UUID organizationId, UUID userId) {
        addToRole(projectId, organizationId, "Viewers", userId);
    }

    public void addMember(UUID projectId, UUID organizationId, UUID userId) {
        addToRole(projectId, organizationId, "Members", userId);
    }

    /**
     * The first workspace this account belongs to.
     *
     * An account can now be in several, so "the" organization is only meaningful
     * in a test that put it in exactly one.
     */
    @Transactional(readOnly = true)
    public UUID organizationOf(UUID userId) {
        return members.activeFor(userId).getFirst().getOrganizationId();
    }

    /**
     * Delegates to the real provisioning rather than reproducing it.
     *
     * This used to insert the statuses and types itself, which then made
     * IssueMetadataProvisioning.ensureDefaults short-circuit on its
     * "statuses already exist" check -- so the default workflow was never
     * created and every issue creation died on "No workflow configured".
     *
     * A fixture that mirrors production setup will drift from it the moment
     * production setup gains a step. Calling the real thing cannot.
     */
    @Transactional
    public UUID ensureTaskType(UUID organizationId) {
        metadataProvisioning.ensureDefaults(organizationId);
        return issueTypes.findByOrganizationIdOrderByHierarchyLevelDescNameAsc(organizationId).stream()
                .filter(t -> t.getName().equals("Task"))
                .map(IssueType::getId).findFirst().orElseThrow();
    }

    /**
     * Builds the same JwtAuthenticationToken the resource server would produce,
     * so CurrentUser and PermissionService behave identically to a real request.
     * The token is never verified here -- no signature is involved.
     */
    public void authenticateAs(UUID userId, UUID organizationId) {
        authenticateAs(userId, organizationId, "MEMBER");
    }

    public void authenticateAs(UUID userId, UUID organizationId, String role) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
