package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "custom_field")
public class CustomFieldDefinition extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * NULL means the field applies to every project in the org.
     */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private CustomFieldType fieldType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> config = new HashMap<>();

    protected CustomFieldDefinition() {
    }

    public CustomFieldDefinition(UUID organizationId, UUID projectId, String fieldKey,
                                 String name, CustomFieldType fieldType) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.fieldKey = fieldKey;
        this.name = name;
        this.fieldType = fieldType;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> allowedOptions() {
        Object options = config.get("options");
        return options instanceof java.util.List<?> list
                ? (java.util.List<String>) list
                : java.util.List.of();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getName() {
        return name;
    }

    public CustomFieldType getFieldType() {
        return fieldType;
    }

    public Map<String, Object> getConfig() {
        return Map.copyOf(config);
    }
}
