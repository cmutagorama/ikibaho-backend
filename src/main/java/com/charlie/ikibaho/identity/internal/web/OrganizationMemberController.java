package com.charlie.ikibaho.identity.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.identity.internal.application.InvitationService;
import com.charlie.ikibaho.platform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/organization/members")
class OrganizationMemberController {

    private final InvitationService invitations;
    private final CurrentUser currentUser;

    OrganizationMemberController(InvitationService invitations, CurrentUser currentUser) {
        this.invitations = invitations;
        this.currentUser = currentUser;
    }

    private static MemberResponse toResponse(InvitationService.MemberView m) {
        return new MemberResponse(m.userId(), m.email(), m.displayName(),
                m.avatarUrl(), m.status(), m.role());
    }

    /**
     * Membership is an organization-level concern, so it is gated on the caller's
     * role in this organization rather than a project permission -- there is no
     * project to scope against.
     */
    @PostMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse invite(@Valid @RequestBody InviteRequest request) {
        var created = invitations.invite(currentUser.requireOrganizationId(),
                currentUser.requireId(), request.email(), request.displayName());

        // The raw token is returned because there is no mail module yet (backend
        // phase 9). Once email exists, drop it from the response -- an invitation
        // link in an API response is a link anyone with API access can use.
        return new InvitationResponse(created.invitationId(), created.userId(),
                created.email(), created.rawToken(), created.expiresAt(),
                created.existingAccount());
    }

    @GetMapping
    List<MemberResponse> members() {
        return invitations.members(currentUser.requireOrganizationId()).stream()
                .map(OrganizationMemberController::toResponse)
                .toList();
    }

    @DeleteMapping("/{userId}/invitation")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvitation(@PathVariable UUID userId) {
        invitations.revoke(currentUser.requireOrganizationId(), userId);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID userId) {
        invitations.deactivate(currentUser.requireOrganizationId(),
                currentUser.requireId(), userId);
    }

    record InviteRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String displayName) {
    }

    /**
     * @param existingAccount the invitee already has a password, so the client
     *                        offers "sign in to accept" instead of a password field
     */
    record InvitationResponse(UUID invitationId, UUID userId, String email,
                              String token, Instant expiresAt, boolean existingAccount) {
    }

    record MemberResponse(UUID id, String email, String displayName, String avatarUrl,
                          String status, String role) {
    }
}
