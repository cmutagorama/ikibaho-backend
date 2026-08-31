package com.charlie.ikibaho.project.internal.application;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.project.internal.domain.PermissionGrant;
import com.charlie.ikibaho.project.internal.domain.Project;
import com.charlie.ikibaho.project.internal.persistence.PermissionGrantRepository;
import com.charlie.ikibaho.project.internal.persistence.PermissionQueries;
import com.charlie.ikibaho.project.internal.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class PermissionServiceImpl implements PermissionService {
    private final ProjectRepository projects;
    private final PermissionGrantRepository grants;
    private final PermissionQueries queries;
    private final PermissionCache cache;

    PermissionServiceImpl(ProjectRepository projects, PermissionGrantRepository grants,
                          PermissionQueries queries, PermissionCache cache) {
        this.projects = projects;
        this.grants = grants;
        this.queries = queries;
        this.cache = cache;
    }

    @Override
    public boolean hasPermission(UUID userId, UUID projectId, Permission permission) {
        return permissionsFor(userId, projectId).contains(permission);
    }

    @Override
    public boolean hasPermission(UUID userId, IssueContext issue, Permission permission) {
        return resolve(userId, issue.projectId(), issue).contains(permission);
    }

    @Override
    public Set<Permission> permissionsFor(UUID userId, UUID projectId) {
        return cache.get(userId, projectId, () -> resolve(userId, projectId, null));
    }

    @Override
    public void require(UUID userId, UUID projectId, Permission permission) {
        Set<Permission> held = permissionsFor(userId, projectId);

        // 404 before 403: never confirm a resource exists to someone who cannot see it.
        if (!held.contains(Permission.BROWSE_PROJECT)) {
            throw new NotFoundException("Project", projectId);
        }
        if (!held.contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    @Override
    public Set<UUID> browsableProjectIds(UUID userId) {
        return cache.browsable(userId, () -> {
            UUID orgId = cache.organizationId();
            return queries.browsableProjectIds(orgId, userId);
        });
    }

    @Override
    public void require(UUID userId, IssueContext issue, Permission permission) {
        Set<Permission> held = resolve(userId, issue.projectId(), issue);
        if (!held.contains(Permission.BROWSE_PROJECT)) {
            throw new NotFoundException("Issue", issue.issueId());
        }
        if (!held.contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    @Override
    public Set<Permission> permissionsFor(UUID userId, IssueContext issue) {
        // Deliberately uncached: PermissionCache is keyed on (user, project) and would
        // return the context-free answer, silently dropping REPORTER/ASSIGNEE grants.
        return resolve(userId, issue.projectId(), issue);
    }

    /**
     * The core algorithm. Issue context is null for project-level checks.
     */
    private Set<Permission> resolve(UUID userId, UUID projectId, IssueContext issue) {
        Optional<Project> maybe = projects.findById(projectId);
        if (maybe.isEmpty()) {
            return Set.of();                    // unknown project -> no permissions
        }
        Project project = maybe.get();

        // Tenancy: a token from another org resolves to nothing, it does not error.
        if (!project.getOrganizationId().equals(cache.organizationId())) {
            return Set.of();
        }

        Set<UUID> roleIds = queries.roleIdsFor(projectId, userId);
        Set<UUID> groupIds = queries.groupIdsFor(userId);
        List<PermissionGrant> schemeGrants = grants.findBySchemeId(project.getPermissionSchemeId());

        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (PermissionGrant grant : schemeGrants) {
            if (matches(grant, userId, roleIds, groupIds, project, issue)) {
                result.add(grant.getPermission());
            }
        }
        return result;
    }

    private boolean matches(PermissionGrant grant, UUID userId, Set<UUID> roleIds, Set<UUID> groupIds, Project project, IssueContext issue) {
        return switch (grant.getHolderType()) {
            case ANY_LOGGED_IN -> true;
            case USER -> userId.equals(grant.getHolderRef());
            case GROUP -> groupIds.contains(grant.getHolderRef());
            case PROJECT_ROLE -> roleIds.contains(grant.getHolderRef());
            case PROJECT_LEAD -> userId.equals(project.getLeadId());
            // Dynamic issue holders are unsatisfiable without issue context.
            case REPORTER -> issue != null && userId.equals(issue.reporterId());
            case ASSIGNEE -> issue != null && userId.equals(issue.assigneeId());
        };
    }
}
