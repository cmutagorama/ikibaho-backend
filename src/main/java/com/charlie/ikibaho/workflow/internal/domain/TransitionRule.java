package com.charlie.ikibaho.workflow.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transition_rule")
public class TransitionRule extends BaseEntity {
    @Column(name = "transition_id", nullable = false)
    private UUID transitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> config = new HashMap<>();

    protected TransitionRule() {
    }

    public TransitionRule(UUID transitionId, RuleKind kind, RuleType ruleType,
                          Map<String, Object> config) {
        this.transitionId = transitionId;
        this.kind = kind;
        this.ruleType = ruleType;
        if (config != null) this.config.putAll(config);
    }

    public UUID getTransitionId() {
        return transitionId;
    }

    public RuleKind getKind() {
        return kind;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public Map<String, Object> getConfig() {
        return Map.copyOf(config);
    }
}
