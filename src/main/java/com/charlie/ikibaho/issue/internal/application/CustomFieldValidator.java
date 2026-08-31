package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.internal.domain.CustomFieldDefinition;
import com.charlie.ikibaho.issue.internal.persistence.CustomFieldRepository;
import com.charlie.ikibaho.platform.error.ValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * jsonb accepts anything, so type safety for custom fields has to live here.
 */
@Component
public class CustomFieldValidator {
    private final CustomFieldRepository definitions;

    CustomFieldValidator(CustomFieldRepository definitions) {
        this.definitions = definitions;
    }

    public void validate(UUID organizationId, UUID projectId, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;

        Map<String, CustomFieldDefinition> byKey =
                definitions.findApplicable(organizationId, projectId).stream()
                        .collect(Collectors.toMap(CustomFieldDefinition::getFieldKey, Function.identity()));

        values.forEach((key, value) -> {
            CustomFieldDefinition def = byKey.get(key);
            if (def == null) {
                throw new ValidationException("Unknown custom field: " + key);
            }
            if (value == null) return;
            checkType(def, value);
        });
    }

    private void checkType(CustomFieldDefinition def, Object value) {
        switch (def.getFieldType()) {
            case TEXT -> require(value instanceof String, def, "a string");
            case NUMBER -> require(value instanceof Number, def, "a number");
            case CHECKBOX -> require(value instanceof Boolean, def, "a boolean");
            case DATE -> {
                require(value instanceof String, def, "an ISO-8601 date string");
                try {
                    LocalDate.parse((String) value);
                } catch (DateTimeParseException e) {
                    throw new ValidationException(
                            "Custom field '" + def.getFieldKey() + "' must be an ISO-8601 date");
                }
            }
            case USER -> {
                require(value instanceof String, def, "a user id");
                try {
                    UUID.fromString((String) value);
                } catch (IllegalArgumentException e) {
                    throw new ValidationException(
                            "Custom field '" + def.getFieldKey() + "' must be a UUID");
                }
            }
            case SELECT -> {
                require(value instanceof String, def, "one of the configured options");
                requireOption(def, (String) value);
            }
            case MULTI_SELECT -> {
                require(value instanceof List<?>, def, "a list of options");
                ((List<?>) value).forEach(v -> {
                    require(v instanceof String, def, "a list of strings");
                    requireOption(def, (String) v);
                });
            }
        }
    }

    private void requireOption(CustomFieldDefinition def, String value) {
        List<String> allowed = def.allowedOptions();
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            throw new ValidationException(
                    "Custom field '" + def.getFieldKey() + "' must be one of " + allowed);
        }
    }

    private void require(boolean condition, CustomFieldDefinition def, String expected) {
        if (!condition) {
            throw new ValidationException(
                    "Custom field '" + def.getFieldKey() + "' must be " + expected);
        }
    }
}
