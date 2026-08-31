package com.charlie.ikibaho.workflow.internal.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class StatusNameLookup {
    private final JdbcClient jdbc;

    StatusNameLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, String> byIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.sql("SELECT id, name FROM status WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(UUID[]::new))
                .query(StatusName.class)
                .list().stream()
                .collect(Collectors.toMap(StatusName::id, StatusName::name));
    }

    public record StatusName(UUID id, String name) {
    }
}
