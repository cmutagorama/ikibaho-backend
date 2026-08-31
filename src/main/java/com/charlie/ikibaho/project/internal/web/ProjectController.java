package com.charlie.ikibaho.project.internal.web;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.project.ProjectSummary;
import com.charlie.ikibaho.project.internal.application.ProjectServiceImpl;
import com.charlie.ikibaho.project.internal.web.dto.AddActorRequest;
import com.charlie.ikibaho.project.internal.web.dto.CreateProjectRequest;
import com.charlie.ikibaho.project.internal.web.dto.DeletedProjectResponse;
import com.charlie.ikibaho.project.internal.web.dto.ProjectRoleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/projects")
public class ProjectController {
    private final ProjectServiceImpl projects;
    private final CurrentUser currentUser;

    ProjectController(ProjectServiceImpl projects,
                      CurrentUser currentUser) {
        this.projects = projects;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectSummary create(@Valid @RequestBody CreateProjectRequest request) {
        return projects.create(currentUser.requireOrganizationId(), request.key(), request.name(), currentUser.requireId());
    }

    /**
     * Only projects the caller may browse -- filtered in SQL, never post-filtered.
     */
    @GetMapping
    List<ProjectSummary> list() {
        return projects.listForUser(currentUser.requireId());
    }

    @GetMapping("/{projectId}")
    ProjectSummary get(@PathVariable UUID projectId) {
        return projects.getForUser(projectId, currentUser.requireId());
    }

    /**
     * Lets a UI hide buttons the user cannot use.
     */
    @GetMapping("/{projectId}/my-permissions")
    Set<Permission> myPermissions(@PathVariable UUID projectId) {
        return projects.permissionsForUser(projectId, currentUser.requireId());
    }

    /**
     * Roles are org-level definitions; actors are per-project. Both are returned
     * together because the members screen needs empty roles too -- you cannot add
     * someone to "Developers" if the role is invisible until it has a member.
     */
    @GetMapping("/{projectId}/roles")
    List<ProjectRoleResponse> roles(@PathVariable UUID projectId) {
        return projects.rolesWithActors(projectId, currentUser.requireId());
    }

    @PostMapping("/{projectId}/roles/{roleId}/actors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addActor(@PathVariable UUID projectId, @PathVariable UUID roleId,
                  @Valid @RequestBody AddActorRequest request) {
        projects.addRoleActor(projectId, roleId, request.userId(), request.groupId(),
                currentUser.requireId());
    }

    @DeleteMapping("/{projectId}/roles/{roleId}/actors/{actorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeActor(@PathVariable UUID projectId, @PathVariable UUID roleId,
                     @PathVariable UUID actorId) {
        projects.removeRoleActor(projectId, roleId, actorId, currentUser.requireId());
    }

    /**
     * Deleted projects are outside the permission-scheme machinery -- their grants
     * cannot be resolved because the project cannot be loaded. Gate on the global role.
     */
    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    List<DeletedProjectResponse> listDeleted() {
        return projects.listDeleted(currentUser.requireOrganizationId()).stream()
                .map(r -> new DeletedProjectResponse(r.id(), r.key(), r.name(), r.deletedAt()))
                .toList();
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    ProjectSummary restore(@PathVariable UUID projectId) {
        return projects.restore(projectId, currentUser.requireOrganizationId());
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID projectId) {
        projects.delete(projectId, currentUser.requireId());
    }
}
