package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.events.IssueCommented;
import com.charlie.ikibaho.issue.internal.domain.Comment;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.persistence.CommentRepository;
import com.charlie.ikibaho.issue.internal.persistence.IssueRepository;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository comments;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;
    private final IssueRepository issues;
    private final ApplicationEventPublisher events;

    CommentService(CommentRepository comments, PermissionService permissions,
                   IssueContextResolver contexts, IssueRepository issues,
                   ApplicationEventPublisher events) {
        this.comments = comments;
        this.permissions = permissions;
        this.contexts = contexts;
        this.issues = issues;
        this.events = events;
    }

    @Transactional
    public Comment add(UUID issueId, UUID actorId, String body) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.COMMENT_ISSUE);

        Comment saved = comments.save(new Comment(issueId, actorId, body));

        // IssueContext carries no key, so read it here rather than widening a record
        // the project module owns. One indexed lookup per comment.
        String issueKey = issues.findById(issueId).map(Issue::getIssueKey).orElseThrow();

        events.publishEvent(new IssueCommented(issueId, saved.getId(), ctx.projectId(),
                issueKey, actorId, IssueCommented.excerptOf(body), Instant.now()));
        return saved;
    }

    public List<Comment> list(UUID issueId, UUID actorId, int page, int size) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);
        return comments.findByIssueIdOrderByCreatedAtAsc(issueId, PageRequest.of(page, size));
    }

    @Transactional
    public Comment edit(UUID commentId, UUID actorId, String body) {
        Comment comment = load(commentId);
        // Editing is author-only, regardless of project permissions.
        if (!comment.isAuthoredBy(actorId)) {
            throw new ForbiddenException("You can only edit your own comments");
        }
        IssueContext ctx = requireContext(comment.getIssueId());
        permissions.require(actorId, ctx, Permission.COMMENT_ISSUE);

        comment.edit(body);
        return comment;
    }

    @Transactional
    public void delete(UUID commentId, UUID actorId) {
        Comment comment = load(commentId);
        IssueContext ctx = requireContext(comment.getIssueId());

        // Authors may always delete their own; anyone else needs DELETE_COMMENT.
        if (!comment.isAuthoredBy(actorId)) {
            permissions.require(actorId, ctx, Permission.DELETE_COMMENT);
        } else {
            permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);
        }
        comments.deleteById(commentId);
    }

    private Comment load(UUID commentId) {
        return comments.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));
    }

    private IssueContext requireContext(UUID issueId) {
        return contexts.resolve(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }
}
