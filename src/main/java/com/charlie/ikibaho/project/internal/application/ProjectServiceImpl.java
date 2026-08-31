package com.charlie.ikibaho.project.internal.application;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.project.ProjectService;
import com.charlie.ikibaho.project.ProjectSummary;
import com.charlie.ikibaho.project.internal.domain.PermissionScheme;
import com.charlie.ikibaho.project.internal.domain.Project;
import com.charlie.ikibaho.project.internal.domain.ProjectRole;
import com.charlie.ikibaho.project.internal.domain.ProjectRoleActor;
import com.charlie.ikibaho.project.internal.persistence.*;
import com.charlie.ikibaho.project.internal.web.dto.ActorResponse;
import com.charlie.ikibaho.project.internal.web.dto.ProjectRoleResponse;
import com.charlie.ikibaho.project.events.ProjectCreated;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final ProjectRoleActorRepository actors;
    private final ProvisioningService provisioning;
    private final UserService users;
    private final ProjectQueries queries;
    private final PermissionService permissions;
    private final PermissionQueries permissionQueries;
    private final ProjectRoleQueries roleQueries;
    private final ApplicationEventPublisher events;

    ProjectServiceImpl(
            ProjectRepository projects,
            ProjectRoleRepository roles,
            ProjectRoleActorRepository actors,
            ProvisioningService provisioning,
            UserService users,
            ProjectQueries queries,
            PermissionService permissions,
            PermissionQueries permissionQueries,
            ProjectRoleQueries roleQueries,
            ApplicationEventPublisher events) {
        this.projects = projects;
        this.roles = roles;
        this.actors = actors;
        this.provisioning = provisioning;
        this.users = users;
        this.queries = queries;
        this.permissions = permissions;
        this.permissionQueries = permissionQueries;
        this.roleQueries = roleQueries;
        this.events = events;
    }

    static ProjectSummary toSummary(Project p) {
        return new ProjectSummary(p.getId(), p.getOrganizationId(), p.getKey(),
                p.getName(), p.getLeadId());
    }

    @Override
    @Transactional
    public ProjectSummary create(UUID organizationId, String key, String name, UUID leadId) {
        String normalizedKey = key.trim().toUpperCase(Locale.ROOT);

        if (!users.userExistsInOrganization(leadId, organizationId)) {
            throw new NotFoundException("User", leadId);
        }

        if (projects.keyInUse(organizationId, normalizedKey)) {
            throw new ConflictException("Project key already in use: " + normalizedKey);
        }

        PermissionScheme scheme = provisioning.ensureDefaults(organizationId);
        Project project;
        try {
            project = projects.saveAndFlush(
                    new Project(organizationId, normalizedKey, name.trim(), leadId, scheme.getId()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Project key already in use: " + normalizedKey);
        }

        // Without this the creator cannot browse the project they just created.
        ProjectRole admins = roles
                .findByOrganizationIdAndName(organizationId, provisioning.defaultAdminRoleName())
                .orElseThrow(() -> new IllegalStateException("Administrators role missing"));
        actors.save(ProjectRoleActor.forUser(project.getId(), admins.getId(), leadId));

        events.publishEvent(new ProjectCreated(project.getId(), organizationId,
                project.getKey(), project.getName(), leadId, Instant.now()));

        return toSummary(project);
    }

    /**
     * Viewing membership needs only BROWSE_PROJECT: knowing who is on a project is
     * not privileged, and hiding it would make the members screen useless to the
     * people who need to find an assignee.
     */
    public List<ProjectRoleResponse> rolesWithActors(UUID projectId, UUID actingUserId) {
        permissions.require(actingUserId, projectId, Permission.BROWSE_PROJECT);

        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));

        Map<UUID, List<ProjectRoleQueries.RoleActorRow>> byRole =
                roleQueries.findRolesWithActors(projectId, project.getOrganizationId()).stream()
                        .collect(Collectors.groupingBy(ProjectRoleQueries.RoleActorRow::roleId,
                                LinkedHashMap::new, Collectors.toList()));

        return byRole.values().stream().map(rows -> {
            ProjectRoleQueries.RoleActorRow first = rows.getFirst();
            List<ActorResponse> roleActors = rows.stream()
                    // The LEFT JOIN yields one all-null actor row for a role with no members.
                    .filter(r -> r.actorId() != null)
                    .map(r -> r.userId() != null
                            ? new ActorResponse(r.actorId(), "USER", r.userId(),
                                    r.userDisplayName(), r.userEmail(), r.userAvatarUrl(), null, null)
                            : new ActorResponse(r.actorId(), "GROUP", null,
                                    null, null, null, r.groupId(), r.groupName()))
                    .toList();
            return new ProjectRoleResponse(
                    first.roleId(), first.roleName(), first.roleDescription(), roleActors);
        }).toList();
    }

    @Transactional
    public void removeRoleActor(UUID projectId, UUID roleId, UUID actorId, UUID actingUserId) {
        permissions.require(actingUserId, projectId, Permission.ADMINISTER_PROJECT);

        ProjectRoleActor actor = actors.findById(actorId)
                .orElseThrow(() -> new NotFoundException("ProjectRoleActor", actorId));

        // Path and row must agree, or an actor id from another project could be deleted.
        if (!actor.getProjectId().equals(projectId) || !actor.getRoleId().equals(roleId)) {
            throw new NotFoundException("ProjectRoleActor", actorId);
        }
        actors.delete(actor);
    }

    @Transactional
    public void addRoleActor(UUID projectId, UUID roleId, UUID userId, UUID groupId,
                             UUID actingUserId) {
        permissions.require(actingUserId, projectId, Permission.ADMINISTER_PROJECT);

        if ((userId == null) == (groupId == null)) {
            throw new ValidationException("Provide exactly one of userId or groupId");
        }

        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
        UUID orgId = project.getOrganizationId();   // tenant from the row, never the request

        if (!roles.existsByIdAndOrganizationId(roleId, orgId)) {
            throw new NotFoundException("ProjectRole", roleId);
        }

        if (userId != null) {
            if (!users.userExistsInOrganization(userId, orgId)) {
                throw new NotFoundException("User", userId);
            }
            if (actors.existsByProjectIdAndRoleIdAndUserId(projectId, roleId, userId)) {
                throw new ConflictException("User already holds that role on this project");
            }
            actors.save(ProjectRoleActor.forUser(projectId, roleId, userId));
        } else {
            if (!users.groupExistsInOrganization(groupId, orgId)) {
                throw new NotFoundException("Group", groupId);
            }
            if (actors.existsByProjectIdAndRoleIdAndGroupId(projectId, roleId, groupId)) {
                throw new ConflictException("Group already holds that role on this project");
            }
            actors.save(ProjectRoleActor.forGroup(projectId, roleId, groupId));
        }
    }

    @Transactional
    public void delete(UUID projectId, UUID actingUserId) {
        permissions.require(actingUserId, projectId, Permission.ADMINISTER_PROJECT);
        if (projects.softDelete(projectId) == 0) {
            throw new NotFoundException("Project", projectId);
        }
    }

    @Transactional
    public ProjectSummary restore(UUID projectId, UUID organizationId) {
        String key = queries.keyOfDeletedProject(projectId, organizationId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));

        if (projects.keyInUse(organizationId, key)) {
            throw new ConflictException(
                    "Project key " + key + " is in use; rename the active project first");
        }
        if (projects.restore(projectId) == 0) {
            throw new NotFoundException("Project", projectId);
        }
        return projects.findById(projectId)
                .map(ProjectServiceImpl::toSummary)
                .orElseThrow(() -> new IllegalStateException("Project vanished after restore"));
    }

    public ProjectSummary getForUser(UUID projectId, UUID userId) {
        permissions.require(userId, projectId, Permission.BROWSE_PROJECT);
        return projects.findById(projectId)
                .map(ProjectServiceImpl::toSummary)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
    }

    public Set<Permission> permissionsForUser(UUID projectId, UUID userId) {
        permissions.require(userId, projectId, Permission.BROWSE_PROJECT);
        return permissions.permissionsFor(userId, projectId);
    }

    /**
     * One query, not N -- browsable IDs are resolved in SQL, then fetched in a batch.
     */
    public List<ProjectSummary> listForUser(UUID userId) {
        Set<UUID> ids = permissions.browsableProjectIds(userId);
        return ids.isEmpty() ? List.of() : queries.findSummaries(ids);
    }

    public List<ProjectQueries.DeletedProjectRow> listDeleted(UUID organizationId) {
        return queries.findDeleted(organizationId);
    }

    @Override
    public Optional<ProjectSummary> findById(UUID id) {
        return projects.findById(id).map(ProjectServiceImpl::toSummary);
    }

    @Override
    public Optional<ProjectSummary> findByKey(UUID organizationId, String key) {
        return projects.findByOrganizationIdAndKey(organizationId, key.toUpperCase(Locale.ROOT))
                .map(ProjectServiceImpl::toSummary);
    }

    @Override
    @Transactional
    public long nextIssueNumber(UUID projectId) {
        Long next = projects.incrementIssueCounter(projectId);
        if (next == null) throw new NotFoundException("Project", projectId);
        return next;
    }

    /**
     * Role membership, including membership acquired through a group.
     * Returns false rather than throwing: this backs a workflow condition, and a
     * condition that throws turns an unavailable transition into a 500.
     */
    @Override
    public boolean hasRole(UUID userId, UUID projectId, String roleName) {
        if (userId == null || projectId == null || roleName == null) {
            return false;
        }

        Project project = projects.findById(projectId).orElse(null);
        if (project == null) {
            return false;              // unknown or soft-deleted project
        }

        UUID roleId = roles.findByOrganizationIdAndName(project.getOrganizationId(), roleName)
                .map(ProjectRole::getId)
                .orElse(null);
        if (roleId == null) {
            return false;              // misconfigured rule denies
        }

        // roleIdsFor already unions direct actors with group-derived ones.
        return permissionQueries.roleIdsFor(projectId, userId).contains(roleId);
    }

    /**
     * Empty when the project does not exist or has no workflow attached --
     * Optional.map yields empty for a null value, so both collapse correctly.
     */
    @Override
    public Optional<UUID> workflowIdFor(UUID projectId) {
        return projects.findById(projectId).map(Project::getWorkflowId);
    }

    /**
     * Module SPI, called during provisioning. No permission check: the caller is a
     * trusted module, not an HTTP request. If this is ever exposed over REST it needs
     * an ADMINISTER_PROJECT gate.
     */
    @Override
    @Transactional
    public void assignWorkflow(UUID projectId, UUID workflowId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
        project.assignWorkflow(workflowId);
        // Managed entity: dirty checking flushes at commit, no save() needed.
    }
}
