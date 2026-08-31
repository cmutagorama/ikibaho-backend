package com.charlie.ikibaho.notification.internal.application;

import com.charlie.ikibaho.notification.internal.domain.Notification;
import com.charlie.ikibaho.notification.internal.persistence.NotificationRepository;
import com.charlie.ikibaho.notification.internal.web.dto.NotificationResponse;
import com.charlie.ikibaho.platform.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;

    NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<NotificationResponse> inbox(UUID userId, boolean unreadOnly, int limit) {
        var page = PageRequest.ofSize(limit);
        List<Notification> rows = unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNullOrderByOccurredAtDesc(userId, page)
                : notifications.findByRecipientIdOrderByOccurredAtDesc(userId, page);
        return rows.stream().map(NotificationService::toResponse).toList();
    }

    public long unreadCount(UUID userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID userId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId));

        // 404 rather than 403: whether someone else's notification exists is not
        // information this user is entitled to.
        if (!notification.belongsTo(userId)) {
            throw new NotFoundException("Notification", notificationId);
        }

        notification.markRead(Instant.now());
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getKind().name(), n.getTitle(), n.getBody(),
                n.getIssueId(), n.getIssueKey(), n.getProjectId(), n.getReadAt(), n.getOccurredAt());
    }
}
