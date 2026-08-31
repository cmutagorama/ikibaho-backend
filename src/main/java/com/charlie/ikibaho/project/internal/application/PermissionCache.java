package com.charlie.ikibaho.project.internal.application;

import com.charlie.ikibaho.project.Permission;

import com.charlie.ikibaho.platform.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Memoizes permission resolution for the duration of one HTTP request.
 * Outside a request (tests, scheduled jobs, async event listeners) it simply
 * does not cache -- correctness never depends on the cache being present.
 */
@Component
public class PermissionCache {
    private static final String PERMISSIONS_KEY = PermissionCache.class.getName() + ".permissions";
    private static final String BROWSABLE_KEY = PermissionCache.class.getName() + ".browsable";

    private final CurrentUser currentUser;

    PermissionCache(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    Set<Permission> get(UUID userId, UUID projectId, Supplier<Set<Permission>> loader) {
        Map<String, Set<Permission>> cache = scoped(PERMISSIONS_KEY);
        if (cache == null) return loader.get();
        return cache.computeIfAbsent(userId + ":" + projectId, k -> loader.get());
    }

    Set<UUID> browsable(UUID userId, Supplier<Set<UUID>> loader) {
        Map<UUID, Set<UUID>> cache = scoped(BROWSABLE_KEY);
        if (cache == null) return loader.get();
        return cache.computeIfAbsent(userId, k -> loader.get());
    }

    UUID organizationId() {
        return currentUser.requireOrganizationId();
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> scoped(String key) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;                       // no request -> no caching, still correct
        }
        Object existing = attrs.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        if (existing == null) {
            existing = new HashMap<K, V>();
            attrs.setAttribute(key, existing, RequestAttributes.SCOPE_REQUEST);
        }
        return (Map<K, V>) existing;
    }
}
