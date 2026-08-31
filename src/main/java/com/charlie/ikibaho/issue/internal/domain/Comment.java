package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import java.util.UUID;

@Entity
@Table(name = "comment")
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)
public class Comment extends BaseEntity {
    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    protected Comment() { }

    public Comment(UUID issueId, UUID authorId, String body) {
        this.issueId = issueId;
        this.authorId = authorId;
        this.body = body;
    }

    public void edit(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body must not be blank");
        }
        this.body = body;
    }

    public boolean isAuthoredBy(UUID userId) { return authorId.equals(userId); }

    public UUID getIssueId()  { return issueId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody()   { return body; }
}
