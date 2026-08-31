package com.charlie.ikibaho.workflow.internal.application;

import com.charlie.ikibaho.workflow.internal.domain.RuleType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RuleRegistry {
    private final Map<RuleType, TransitionCondition> conditions;
    private final Map<RuleType, TransitionValidator> validators;
    private final Map<RuleType, TransitionPostFunction> postFunctions;

    RuleRegistry(List<TransitionCondition> conditions,
                 List<TransitionValidator> validators,
                 List<TransitionPostFunction> postFunctions) {
        this.conditions = index(conditions, TransitionCondition::type);
        this.validators = index(validators, TransitionValidator::type);
        this.postFunctions = index(postFunctions, TransitionPostFunction::type);
    }

    /**
     * toMap throws on duplicate keys -- two beans claiming one RuleType fails at startup.
     */
    private static <T> Map<RuleType, T> index(List<T> beans, Function<T, RuleType> key) {
        return beans.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    Optional<TransitionCondition> condition(RuleType t) {
        return Optional.ofNullable(conditions.get(t));
    }

    Optional<TransitionValidator> validator(RuleType t) {
        return Optional.ofNullable(validators.get(t));
    }

    Optional<TransitionPostFunction> postFunction(RuleType t) {
        return Optional.ofNullable(postFunctions.get(t));
    }
}
