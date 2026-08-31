package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "issue_link")
public class IssueLink extends BaseEntity {
    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    private LinkType linkType;

    protected IssueLink() {
    }

    public IssueLink(UUID sourceId, UUID targetId, LinkType linkType) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("An issue cannot link to itself");
        }
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.linkType = linkType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public LinkType getLinkType() {
        return linkType;
    }
}
