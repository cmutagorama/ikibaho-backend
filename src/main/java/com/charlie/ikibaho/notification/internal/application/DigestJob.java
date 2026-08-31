package com.charlie.ikibaho.notification.internal.application;

import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.notification.internal.domain.DigestState;
import com.charlie.ikibaho.notification.internal.domain.Notification;
import com.charlie.ikibaho.notification.internal.email.EmailSender;
import com.charlie.ikibaho.notification.internal.persistence.DigestStateRepository;
import com.charlie.ikibaho.notification.internal.persistence.NotificationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One email per person per run, covering everything they have not read.
 * <p>
 * A digest rather than an email per notification, because notification volume on
 * a busy project is exactly the volume at which people set up a mail rule to
 * delete them unread.
 */
@Component
public class DigestJob {
    private static final Logger log = LoggerFactory.getLogger(DigestJob.class);

    private final NotificationRepository notifications;
    private final DigestStateRepository digestStates;
    private final UserService users;
    private final EmailSender email;
    private final Duration lookback;

    DigestJob(NotificationRepository notifications, DigestStateRepository digestStates,
              UserService users, EmailSender email,
              @Value("${ikibaho.notifications.digest-lookback:PT24H}") Duration lookback) {
        this.notifications = notifications;
        this.digestStates = digestStates;
        this.users = users;
        this.email = email;
        this.lookback = lookback;
    }

    /**
     * Locked to one instance: three nodes running this unlocked is three copies of
     * every digest.
     */
    @Scheduled(cron = "${ikibaho.notifications.digest-cron:0 0 8 * * *}")
    @SchedulerLock(name = "notificationDigest", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void sendDigests() {
        Instant floor = Instant.now().minus(lookback);
        List<UUID> recipients = notifications.recipientsWithUnreadSince(floor);

        int sent = 0;
        for (UUID recipientId : recipients) {
            if (sendTo(recipientId, floor)) {
                sent++;
            }
        }
        log.info("Sent {} digest email(s) to {} candidate(s)", sent, recipients.size());
    }

    /**
     * One transaction per person.
     * <p>
     * REQUIRES_NEW so that one user whose address is malformed does not roll back
     * everyone else's high-water mark and cause the whole batch to re-send.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean sendTo(UUID recipientId, Instant floor) {
        DigestState state = digestStates.findById(recipientId)
                .orElseGet(() -> digestStates.save(new DigestState(recipientId, floor)));

        // The later of the two: never re-send what the last digest covered, and
        // never reach back further than the lookback for someone new.
        Instant since = state.getLastSentAt().isAfter(floor) ? state.getLastSentAt() : floor;

        List<Notification> unread = notifications
                .findByRecipientIdAndReadAtIsNullAndOccurredAtAfterOrderByOccurredAtAsc(
                        recipientId, since);

        if (unread.isEmpty()) {
            return false;
        }

        UserSummary user = users.findById(recipientId).orElse(null);
        if (user == null) {
            return false;
        }

        email.send(user.email(), subjectFor(unread), bodyFor(user, unread));

        // Advanced only after the send is attempted. EmailSender swallows delivery
        // failures by design, so this is a record of "we tried", not "it arrived" --
        // retrying forever on a permanently bad address would block every later digest.
        state.markSent(Instant.now());
        return true;
    }

    private String subjectFor(List<Notification> unread) {
        return unread.size() == 1
                ? unread.getFirst().getTitle()
                : unread.size() + " updates on your issues";
    }

    private String bodyFor(UserSummary user, List<Notification> unread) {
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(user.displayName()).append(",\n\n")
                .append("You have ").append(unread.size())
                .append(unread.size() == 1 ? " unread notification:\n\n" : " unread notifications:\n\n");

        for (Notification notification : unread) {
            body.append("  * ").append(notification.getTitle());
            if (notification.getBody() != null && !notification.getBody().isBlank()) {
                body.append("\n    ").append(notification.getBody());
            }
            body.append("\n\n");
        }
        body.append("-- Ikibaho\n");
        return body.toString();
    }
}
