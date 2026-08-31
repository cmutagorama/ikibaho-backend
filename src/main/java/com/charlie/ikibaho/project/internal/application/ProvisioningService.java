package com.charlie.ikibaho.project.internal.application;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.project.internal.domain.HolderType;
import com.charlie.ikibaho.project.internal.domain.*;
import com.charlie.ikibaho.project.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ProvisioningService {
    static final String ROLE_ADMINISTRATORS = "Administrators";
    static final String ROLE_MEMBERS = "Members";
    static final String ROLE_VIEWERS = "Viewers";
    static final String DEFAULT_SCHEME = "Default Permission Scheme";

    private final ProjectRoleRepository roles;
    private final PermissionSchemeRepository schemes;
    private final PermissionGrantRepository grants;

    ProvisioningService(ProjectRoleRepository roles, PermissionSchemeRepository schemes, PermissionGrantRepository grants) {
        this.roles = roles;
        this.schemes = schemes;
        this.grants = grants;
    }

    @Transactional
    PermissionScheme ensureDefaults(UUID organizationId) {
        return schemes.findByOrganizationIdAndDefaultSchemeTrue(organizationId).orElseGet(() -> createDefaults(organizationId));
    }

    private PermissionScheme createDefaults(UUID organizationId) {
        ProjectRole admins  = role(organizationId, ROLE_ADMINISTRATORS);
        ProjectRole members = role(organizationId, ROLE_MEMBERS);
        ProjectRole viewers = role(organizationId, ROLE_VIEWERS);

        PermissionScheme scheme = schemes.save(
                new PermissionScheme(organizationId, DEFAULT_SCHEME, true));
        UUID id = scheme.getId();

        // Administrators: everything.
        grantAll(id, EnumSet.allOf(Permission.class), HolderType.PROJECT_ROLE, admins.getId());

        // Members: day-to-day work, no project administration.
        grantAll(id, EnumSet.of(
                        Permission.BROWSE_PROJECT, Permission.CREATE_ISSUE, Permission.EDIT_ISSUE,
                        Permission.ASSIGN_ISSUE, Permission.TRANSITION_ISSUE, Permission.COMMENT_ISSUE,
                        Permission.MANAGE_SPRINT, Permission.MANAGE_BOARD),
                HolderType.PROJECT_ROLE, members.getId());

        // Viewers: read only.
        grantAll(id, EnumSet.of(Permission.BROWSE_PROJECT), HolderType.PROJECT_ROLE, viewers.getId());

        // Dynamic holders: you can always edit and delete what you reported...
        grantAll(id, EnumSet.of(Permission.EDIT_ISSUE, Permission.DELETE_ISSUE),
                HolderType.REPORTER, null);
        // ...and transition what is assigned to you.
        grantAll(id, EnumSet.of(Permission.TRANSITION_ISSUE, Permission.EDIT_ISSUE),
                HolderType.ASSIGNEE, null);

        grantAll(id, EnumSet.of(Permission.ADMINISTER_PROJECT, Permission.BROWSE_PROJECT),
                HolderType.PROJECT_LEAD, null);

        return scheme;
    }

    private ProjectRole role(UUID organizationId, String name) {
        return roles.findByOrganizationIdAndName(organizationId, name).orElseGet(() -> roles.save(new ProjectRole(organizationId, name, null)));
    }

    private void grantAll(UUID schemeId, Set<Permission> permissions, HolderType type, UUID ref) {
        permissions.forEach(p -> grants.save(new PermissionGrant(schemeId, p, type, ref)));
    }

    String defaultAdminRoleName() { return ROLE_ADMINISTRATORS; }
}
