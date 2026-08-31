package com.charlie.ikibaho.project.internal.security;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
public class IkibahoPermissionEvaluator implements PermissionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(IkibahoPermissionEvaluator.class);
    private final PermissionService permissions;
    private final IssueContextResolver issueContexts;

    IkibahoPermissionEvaluator(PermissionService permissions, IssueContextResolver issueContexts) {
        this.permissions = permissions;
        this.issueContexts = issueContexts;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        return false; // we always use the (id, type, permission) form
    }

    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if (!(auth instanceof JwtAuthenticationToken token) || targetId == null) {
            return false;
        }
        UUID userId = UUID.fromString(token.getToken().getSubject());
        Permission required = Permission.valueOf(permission.toString());
        UUID id = toUuid(targetId);
        if (id == null) return false;

        try {
            return switch (targetType) {
                case "PROJECT" -> permissions.hasPermission(userId, id, required);
                case "ISSUE" ->
                        issueContexts.resolve(id).map(ctx -> permissions.hasPermission(userId, ctx, required)).orElse(false);
                default -> {
                    log.warn("Unknown permission target type: {}", targetType);
                    yield false;
                }
            };
        } catch (RuntimeException ex) {
            // A permission evaluator answers yes/no. Anything else becomes a 500
            // that bypasses your error handling entirely.
            log.warn("Permission evaluation failed for {} {}", targetType, targetId, ex);
            return false;
        }
    }

    private static UUID toUuid(Serializable value) {
        try {
            return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
