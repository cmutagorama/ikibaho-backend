package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.IssueLink;
import com.charlie.ikibaho.issue.internal.domain.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {
    List<IssueLink> findBySourceIdOrTargetId(UUID sourceId, UUID targetId);

    boolean existsBySourceIdAndTargetIdAndLinkType(UUID sourceId, UUID targetId, LinkType type);
}
