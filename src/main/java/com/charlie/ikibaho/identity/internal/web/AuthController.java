package com.charlie.ikibaho.identity.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.identity.internal.application.AuthService;
import com.charlie.ikibaho.identity.internal.application.InvalidTokenException;
import com.charlie.ikibaho.identity.internal.application.InvitationService;
import com.charlie.ikibaho.identity.internal.application.LoginResult;
import com.charlie.ikibaho.identity.internal.application.TokenPair;
import com.charlie.ikibaho.identity.internal.web.dto.LoginRequest;
import com.charlie.ikibaho.identity.internal.web.dto.LoginResponse;
import com.charlie.ikibaho.identity.internal.web.dto.RefreshRequest;
import com.charlie.ikibaho.identity.internal.web.dto.RegisterRequest;
import com.charlie.ikibaho.identity.internal.web.dto.TokenResponse;
import com.charlie.ikibaho.platform.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiVersion.V1 + "/auth")
class AuthController {

    private final AuthService authService;
    private final InvitationService invitations;
    private final CurrentUser currentUser;
    private final RefreshTokenCookies cookies;

    AuthController(AuthService authService, InvitationService invitations,
                   CurrentUser currentUser, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.invitations = invitations;
        this.currentUser = currentUser;
        this.cookies = cookies;
    }

    /**
     * Public: the invitee has no session yet, and the invitation token is the only
     * credential they hold.
     */
    @PostMapping("/accept-invitation")
    LoginResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                   @RequestParam(defaultValue = "false") boolean cookie,
                                   HttpServletResponse response) {
        return deliver(invitations.accept(request.token(), request.password()), cookie, response);
    }

    /**
     * Accepting into a second workspace, for somebody who already has an account.
     *
     * Authenticated on purpose: the invitation link proves only that an email was
     * received, and the account it attaches to already exists.
     */
    @PostMapping("/accept-invitation/signed-in")
    LoginResponse acceptInvitationSignedIn(@Valid @RequestBody AcceptSignedInRequest request,
                                           @RequestParam(defaultValue = "false") boolean cookie,
                                           HttpServletResponse response) {
        return deliver(invitations.acceptAsExistingUser(request.token(), currentUser.requireId()),
                cookie, response);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    LoginResponse register(@Valid @RequestBody RegisterRequest request,
                           @RequestParam(defaultValue = "false") boolean cookie,
                           HttpServletResponse response) {
        return deliver(authService.register(request.email(), request.password(),
                request.displayName(), request.organizationName()), cookie, response);
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request,
                        @RequestParam(defaultValue = "false") boolean cookie,
                        HttpServletResponse response) {
        return deliver(authService.login(request.email(), request.password(),
                request.organization()), cookie, response);
    }

    /** Reissues the session against another workspace the caller belongs to. */
    @PostMapping("/switch-organization")
    LoginResponse switchOrganization(@Valid @RequestBody SwitchRequest request,
                                     @RequestParam(defaultValue = "false") boolean cookie,
                                     HttpServletResponse response) {
        return deliver(authService.switchOrganization(currentUser.requireId(),
                request.organization()), cookie, response);
    }

    /**
     * Accepts the refresh token from the body (confidential clients: CLI, mobile,
     * server-side integrations) or from the httpOnly cookie (browsers). The response
     * uses whichever transport the request arrived on.
     */
    @PostMapping("/refresh")
    TokenResponse refresh(@RequestBody(required = false) RefreshRequest request,
                          HttpServletRequest httpRequest,
                          HttpServletResponse response) {
        String fromBody = request == null ? null : request.refreshToken();
        boolean viaCookie = fromBody == null || fromBody.isBlank();

        String token = viaCookie
                ? cookies.read(httpRequest)
                .orElseThrow(() -> new InvalidTokenException("No refresh token supplied"))
                : fromBody;

        return tokens(authService.refresh(token), viaCookie, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletResponse response) {
        authService.logout(currentUser.requireId());
        // Always clear: harmless for body-transport clients, essential for browsers,
        // and the server-side revocation has already happened either way.
        cookies.clear(response);
    }

    /**
     * In cookie mode the refresh token is omitted from the body entirely -- putting it
     * in both places would hand XSS the very value the cookie exists to protect.
     * Jackson is configured to drop nulls, so the field simply is not serialised.
     */
    private LoginResponse deliver(LoginResult result, boolean useCookie,
                                  HttpServletResponse response) {
        // No token yet means the workspace choice is outstanding. Setting a
        // refresh cookie here would create a session for a workspace nobody has
        // picked.
        if (result.needsWorkspaceChoice()) {
            return new LoginResponse(result.userId(), null, result.organizations());
        }
        return new LoginResponse(result.userId(),
                tokens(result.tokens(), useCookie, response), result.organizations());
    }

    private TokenResponse tokens(TokenPair pair, boolean useCookie, HttpServletResponse response) {
        if (useCookie) {
            cookies.write(response, pair.refreshToken());
            return new TokenResponse(pair.accessToken(), null, "Bearer", pair.expiresInSeconds());
        }
        return new TokenResponse(pair.accessToken(), pair.refreshToken(),
                "Bearer", pair.expiresInSeconds());
    }

    record SwitchRequest(@NotBlank String organization) {
    }

    record AcceptSignedInRequest(@NotBlank String token) {
    }

    record AcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 12, max = 128) String password) {
    }
}
