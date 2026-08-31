package com.charlie.ikibaho.notification.internal.application;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.issue.IssueLookup;
import com.charlie.ikibaho.issue.IssueSummary;
import com.charlie.ikibaho.issue.events.IssueAssigned;
import com.charlie.ikibaho.issue.events.IssueCommented;
import com.charlie.ikibaho.notification.internal.domain.Notification;
import com.charlie.ikibaho.notification.internal.domain.NotificationKind;
import com.charlie.ikibaho.notification.internal.email.EmailSender;
import com.charlie.ikibaho.notification.internal.persistence.NotificationRepository;
import com.charlie.ikibaho.notification.internal.web.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who hears about what, and writes the in-app notification.
 * <p>
 * The rule running through all of it: never notify someone about their own
 * action. Being told what you just did is noise, and noisy notifications get
 * muted wholesale -- which costs you the ones that mattered.
 */
@Component
class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final IssueLookup issues;
    private final UserService users;
    private final EmailSender email;
    private final NotificationStream stream;

    NotificationDispatcher(NotificationRepository notifications, IssueLookup issues,
                           UserService users, EmailSender email, NotificationStream stream) {
        this.notifications = notifications;
        this.issues = issues;
        this.users = users;
        this.email = email;
        this.stream = stream;
    }

    @ApplicationModuleListener
    void on(IssueAssigned event) {
        // Assigning something to yourself needs no announcement.
        if (event.assigneeId() == null || event.assigneeId().equals(event.actorId())) {
            return;
        }

        String actor = displayNameOf(event.actorId());
        Notification notification = new Notification(
                event.assigneeId(),
                NotificationKind.ISSUE_ASSIGNED,
                actor + " assigned " + event.issueKey() + " to you",
                event.summary(),
                event.issueId(), event.issueKey(), event.projectId(), event.occurredAt(),
                NotificationKind.ISSUE_ASSIGNED + ":" + event.issueId()
                        + ":" + event.assigneeId() + ":" + event.occurredAt().toEpochMilli());

        if (save(notification)) {
            stream.push(event.assigneeId(), toResponse(notification));
            emailIfPossible(event.assigneeId(), notification);
        }
    }

    @ApplicationModuleListener
    void on(IssueCommented event) {
        // The reporter and the assignee are the people with a stake in the issue.
        // Read through IssueLookup: notification has no business touching issue tables.
        Optional<IssueSummary> issue = issues.findById(event.issueId());
        if (issue.isEmpty()) {
            log.debug("Issue {} is gone; nothing to notify about", event.issueId());
            return;
        }

        Set<UUID> recipients = new LinkedHashSet<>();
        if (issue.get().reporterId() != null) recipients.add(issue.get().reporterId());
        if (issue.get().assigneeId() != null) recipients.add(issue.get().assigneeId());
        recipients.remove(event.authorId());

        if (recipients.isEmpty()) {
            return;
        }

        String author = displayNameOf(event.authorId());
        for (UUID recipient : recipients) {
            Notification notification = new Notification(
                    recipient,
                    NotificationKind.ISSUE_COMMENTED,
                    author + " commented on " + event.issueKey(),
                    event.excerpt(),
                    event.issueId(), event.issueKey(), event.projectId(), event.occurredAt(),
                    NotificationKind.ISSUE_COMMENTED + ":" + event.commentId() + ":" + recipient);

            if (save(notification)) {
                stream.push(recipient, toResponse(notification));
                emailIfPossible(recipient, notification);
            }
        }
    }

    /**
     * @return true if this call is the one that created the row -- so the email
     * is sent once, not once per retry.
     */
    private boolean save(Notification notification) {
        if (notifications.existsByDedupeKey(notification.getDedupeKey())) {
            return false;
        }
        try {
            notifications.save(notification);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private void emailIfPossible(UUID recipientId, Notification notification) {
        users.findById(recipientId).ifPresent(user ->
                email.send(user.email(), notification.getTitle(), notification.getBody()));
    }

    private String displayNameOf(UUID userId) {
        if (userId == null) return "Someone";
        return users.findById(userId).map(UserSummary::displayName).orElse("Someone");
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getKind().name(), n.getTitle(), n.getBody(),
                n.getIssueId(), n.getIssueKey(), n.getProjectId(), n.getReadAt(), n.getOccurredAt());
    }
}
