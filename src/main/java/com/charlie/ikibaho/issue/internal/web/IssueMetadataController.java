package com.charlie.ikibaho.issue.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.issue.internal.application.IssueMetadataProvisioning;
import com.charlie.ikibaho.issue.internal.persistence.IssueTypeRepository;
import com.charlie.ikibaho.issue.internal.persistence.StatusRepository;
import com.charlie.ikibaho.platform.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/meta")
public class IssueMetadataController {
    private final StatusRepository statuses;
    private final IssueTypeRepository types;
    private final CurrentUser currentUser;
    private final IssueMetadataProvisioning provisioning;

    IssueMetadataController(StatusRepository statuses, IssueTypeRepository types,
                            CurrentUser currentUser, IssueMetadataProvisioning provisioning) {
        this.statuses = statuses;
        this.types = types;
        this.currentUser = currentUser;
        this.provisioning = provisioning;
    }

    @GetMapping("/statuses")
    List<StatusResponse> statuses() {
        return statuses.findByOrganizationIdOrderByPositionAsc(currentUser.requireOrganizationId())
                .stream()
                .map(s -> new StatusResponse(s.getId(), s.getName(), s.getCategory().name(), s.getPosition()))
                .toList();
    }

    @GetMapping("/issue-types")
    List<TypeResponse> types() {
        UUID organizationId = currentUser.requireOrganizationId();
        provisioning.ensureDefaults(organizationId);
        return types.findByOrganizationIdOrderByHierarchyLevelDescNameAsc(
                        currentUser.requireOrganizationId())
                .stream()
                .map(t -> new TypeResponse(t.getId(), t.getName(), t.getHierarchyLevel()))
                .toList();
    }

    record StatusResponse(UUID id, String name, String category, int position) {
    }

    record TypeResponse(UUID id, String name, int hierarchyLevel) {
    }
}
