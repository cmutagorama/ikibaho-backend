package com.charlie.ikibaho.platform.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUser {
    public Optional<UUID> id() {
        return jwt().map(j -> UUID.fromString(j.getSubject()));
    }

    public Optional<UUID> organizationId() {
        return jwt().map(j -> UUID.fromString(j.getClaimAsString("org")));
    }

    public UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public UUID requireOrganizationId() {
        return organizationId().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    private Optional<Jwt> jwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? Optional.of(token.getToken()) : Optional.empty();
    }
}
