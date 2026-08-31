package com.charlie.ikibaho.identity.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/users")
public class UserController {
    private final UserService users;
    private final CurrentUser currentUser;

    UserController(UserService users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    UserSummary me() {
        UUID id = currentUser.requireId();
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    /**
     * Same-org lookup only -- a user id from another tenant must look nonexistent.
     */
    @GetMapping("/{userId}")
    UserSummary get(@PathVariable UUID userId) {
        UUID orgId = currentUser.requireOrganizationId();
        if (!users.userExistsInOrganization(userId, orgId)) {
            throw new NotFoundException("User", userId);
        }
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
