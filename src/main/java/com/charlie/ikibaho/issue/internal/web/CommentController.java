package com.charlie.ikibaho.issue.internal.web;

import com.charlie.ikibaho.platform.web.ApiVersion;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.issue.internal.application.CommentService;
import com.charlie.ikibaho.issue.internal.domain.Comment;
import com.charlie.ikibaho.platform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
public class CommentController {
    private final CommentService comments;
    private final UserService users;
    private final CurrentUser currentUser;

    CommentController(CommentService comments, UserService users, CurrentUser currentUser) {
        this.comments = comments;
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping("/issues/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse add(@PathVariable UUID issueId, @Valid @RequestBody CommentRequest req) {
        return toResponse(comments.add(issueId, currentUser.requireId(), req.body()));
    }

    @GetMapping("/issues/{issueId}/comments")
    List<CommentResponse> list(@PathVariable UUID issueId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return comments.list(issueId, currentUser.requireId(), page, size)
                .stream().map(this::toResponse).toList();
    }

    @PatchMapping("/comments/{commentId}")
    CommentResponse edit(@PathVariable UUID commentId, @Valid @RequestBody CommentRequest req) {
        return toResponse(comments.edit(commentId, currentUser.requireId(), req.body()));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID commentId) {
        comments.delete(commentId, currentUser.requireId());
    }

    private CommentResponse toResponse(Comment c) {
        String name = users.findById(c.getAuthorId())
                .map(u -> u.displayName()).orElse("Unknown");
        return new CommentResponse(c.getId(), c.getIssueId(), c.getAuthorId(), name,
                c.getBody(), c.getCreatedAt(), c.getUpdatedAt());
    }

    record CommentRequest(@NotBlank @Size(max = 32_000) String body) {
    }

    record CommentResponse(UUID id, UUID issueId, UUID authorId, String authorName,
                           String body, Instant createdAt, Instant updatedAt) {
    }
}
